package com.aitrainercrm.platform.progressphoto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitrainercrm.platform.auth.dto.RegisterRequest;
import com.aitrainercrm.platform.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * End-to-end coverage for progress photos - see V55's migration comment. Point-in-time-fact shape
 * like {@code PromoRedemptionIntegrationTest}: no status field, no PATCH .../status endpoint,
 * takenAt just set once at creation and never revisited.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ProgressPhotoIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void progressPhotoLifecycle_endToEnd() throws Exception {
        String ownerToken = registerOwner("progress-photo-crud");

        MvcResult contactResult = mockMvc
                .perform(authed(post("/api/v1/contacts"), ownerToken).content("{\"firstName\":\"Jamie\",\"lastName\":\"Client\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String contactId = readField(contactResult, "data", "id");

        MvcResult photoResult = mockMvc
                .perform(authed(post("/api/v1/progress-photos"), ownerToken)
                        .content("{\"contactId\":\"%s\",\"photoUrl\":\"https://example.com/front.jpg\",\"category\":\"FRONT\"}".formatted(contactId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.category").value("FRONT"))
                .andExpect(jsonPath("$.data.takenAt").exists())
                .andReturn();
        String photoId = readField(photoResult, "data", "id");
        assertThat(photoId).isNotBlank();
        String takenAt = readField(photoResult, "data", "takenAt");

        // Editing other fields must never move takenAt - it's a point-in-time fact, not a lifecycle.
        MvcResult editedResult = mockMvc
                .perform(authed(put("/api/v1/progress-photos/" + photoId), ownerToken)
                        .content("{\"photoUrl\":\"https://example.com/front-2.jpg\",\"category\":\"SIDE\",\"notes\":\"After 4 weeks\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.category").value("SIDE"))
                .andReturn();
        // Compared via Instant equality (not raw string/jsonPath equality) - the first response
        // returns the in-memory Instant.now() at full nanosecond precision, while this later
        // response was re-fetched from Postgres, whose timestamptz column only holds microsecond
        // precision, so the two ISO strings can legitimately differ in their last digit while still
        // being the same instant.
        assertThat(Instant.parse(readField(editedResult, "data", "takenAt")))
                .isCloseTo(Instant.parse(takenAt), org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.MICROS));

        mockMvc.perform(authed(get("/api/v1/progress-photos"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(authed(delete("/api/v1/progress-photos/" + photoId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/progress-photos/" + photoId), ownerToken)).andExpect(status().isNotFound());
    }

    private String registerOwner(String prefix) throws Exception {
        String email = "%s-%d@example.com".formatted(prefix, System.nanoTime());
        RegisterRequest registerRequest = new RegisterRequest(email, "Str0ng!Passw0rd", "Owner", "Person", "Acme Rockets");
        MvcResult registerResult = mockMvc
                .perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        return readField(registerResult, "data", "accessToken");
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder, String accessToken) {
        return builder.header("Authorization", "Bearer " + accessToken).contentType(MediaType.APPLICATION_JSON);
    }

    private String readField(MvcResult result, String... path) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        for (String segment : path) {
            node = node.get(segment);
            if (node == null) return "";
        }
        return node.asText();
    }
}
