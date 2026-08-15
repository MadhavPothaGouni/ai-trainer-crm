package com.aitrainercrm.platform.checkin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitrainercrm.platform.auth.dto.RegisterRequest;
import com.aitrainercrm.platform.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * End-to-end coverage for the Client Check-In module - see V52's migration comment and
 * ClientCheckIn's javadoc for the gap this fills. Covers full CRUD plus the free status
 * transition with checkedOutAt stamped once on entering CHECKED_OUT, mirroring
 * TimeOffRequestIntegrationTest's structure.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ClientCheckInIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void createUpdateListAndDeleteClientCheckIn_endToEnd() throws Exception {
        String ownerToken = registerOwner("checkin-crud");
        String contactId = createContact(ownerToken, "Jamie", "Client");

        MvcResult createResult = mockMvc
                .perform(authed(post("/api/v1/client-check-ins"), ownerToken)
                        .content("{\"contactId\":\"%s\",\"method\":\"KIOSK\"}".formatted(contactId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("CHECKED_IN"))
                .andExpect(jsonPath("$.data.method").value("KIOSK"))
                .andExpect(jsonPath("$.data.checkedOutAt").doesNotExist())
                .andReturn();
        String checkInId = readField(createResult, "data", "id");
        assertThat(checkInId).isNotBlank();

        mockMvc.perform(authed(put("/api/v1/client-check-ins/" + checkInId), ownerToken)
                        .content("{\"method\":\"MANUAL\",\"notes\":\"Front desk override\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.method").value("MANUAL"))
                .andExpect(jsonPath("$.data.notes").value("Front desk override"));

        mockMvc.perform(authed(get("/api/v1/client-check-ins"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(authed(delete("/api/v1/client-check-ins/" + checkInId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/client-check-ins/" + checkInId), ownerToken)).andExpect(status().isNotFound());
    }

    @Test
    void statusTransitionToCheckedOut_stampsCheckedOutAtOnlyOnce() throws Exception {
        String ownerToken = registerOwner("checkin-checkout");
        String contactId = createContact(ownerToken, "Riley", "Client");
        String checkInId = createClientCheckIn(ownerToken, contactId);

        mockMvc.perform(authed(patch("/api/v1/client-check-ins/" + checkInId + "/status"), ownerToken)
                        .content("{\"status\":\"CHECKED_OUT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CHECKED_OUT"))
                .andExpect(jsonPath("$.data.checkedOutAt").exists());

        MvcResult firstCheckOutResult = mockMvc
                .perform(authed(get("/api/v1/client-check-ins/" + checkInId), ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        String checkedOutAt = readField(firstCheckOutResult, "data", "checkedOutAt");

        // Moving away and back to CHECKED_OUT must not restamp checkedOutAt.
        mockMvc.perform(authed(patch("/api/v1/client-check-ins/" + checkInId + "/status"), ownerToken)
                        .content("{\"status\":\"CHECKED_IN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CHECKED_IN"))
                .andExpect(jsonPath("$.data.checkedOutAt").value(checkedOutAt));

        mockMvc.perform(authed(patch("/api/v1/client-check-ins/" + checkInId + "/status"), ownerToken)
                        .content("{\"status\":\"CHECKED_OUT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checkedOutAt").value(checkedOutAt));
    }

    private String createContact(String token, String firstName, String lastName) throws Exception {
        MvcResult result = mockMvc
                .perform(authed(post("/api/v1/contacts"), token)
                        .content("{\"firstName\":\"%s\",\"lastName\":\"%s\"}".formatted(firstName, lastName)))
                .andExpect(status().isCreated())
                .andReturn();
        return readField(result, "data", "id");
    }

    private String createClientCheckIn(String token, String contactId) throws Exception {
        MvcResult result = mockMvc
                .perform(authed(post("/api/v1/client-check-ins"), token).content("{\"contactId\":\"%s\",\"method\":\"MANUAL\"}".formatted(contactId)))
                .andExpect(status().isCreated())
                .andReturn();
        return readField(result, "data", "id");
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
