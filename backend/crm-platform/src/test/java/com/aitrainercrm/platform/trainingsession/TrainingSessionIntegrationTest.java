package com.aitrainercrm.platform.trainingsession;

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
 * End-to-end coverage for the Training Session module - see V37's migration comment and
 * TrainingSession's javadoc for the gap this fills (BookingSlot is pre-session scheduling,
 * ClientGoal is the long-term target - this is the post-session record of what actually
 * happened). Covers full CRUD, the free (non-linear) status transition, and validates that a
 * bookingSlotId must belong to the caller's own organization.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class TrainingSessionIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void createUpdateListAndDeleteTrainingSession_endToEnd() throws Exception {
        String ownerToken = registerOwner("session-crud");
        String contactId = createContact(ownerToken, "Jamie", "Client");

        MvcResult createResult = mockMvc
                .perform(authed(post("/api/v1/training-sessions"), ownerToken)
                        .content(
                                """
                                {"contactId":"%s","startedAt":"2026-09-01T15:00:00Z","durationMinutes":45,
                                "sessionType":"IN_PERSON","focusArea":"Lower body","clientRpe":7,"coachNotes":"Solid session"}
                                """
                                        .formatted(contactId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.durationMinutes").value(45))
                .andReturn();
        String sessionId = readField(createResult, "data", "id");
        assertThat(sessionId).isNotBlank();

        mockMvc.perform(authed(put("/api/v1/training-sessions/" + sessionId), ownerToken)
                        .content(
                                """
                                {"startedAt":"2026-09-01T15:00:00Z","durationMinutes":60,
                                "sessionType":"VIRTUAL","focusArea":"Upper body","clientRpe":8,"coachNotes":"Even better"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.durationMinutes").value(60))
                .andExpect(jsonPath("$.data.sessionType").value("VIRTUAL"));

        mockMvc.perform(authed(get("/api/v1/training-sessions"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(authed(delete("/api/v1/training-sessions/" + sessionId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/training-sessions/" + sessionId), ownerToken)).andExpect(status().isNotFound());
    }

    @Test
    void statusTransitionsMoveFreelyInBothDirections() throws Exception {
        String ownerToken = registerOwner("session-status");
        String contactId = createContact(ownerToken, "Riley", "Client");
        String sessionId = createSession(ownerToken, contactId);

        mockMvc.perform(authed(patch("/api/v1/training-sessions/" + sessionId + "/status"), ownerToken).content("{\"status\":\"NO_SHOW\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NO_SHOW"));

        // Correcting a mistaken NO_SHOW back to SCHEDULED - a legitimate correction, same
        // restraint contracts.status/client_goals.status already take.
        mockMvc.perform(authed(patch("/api/v1/training-sessions/" + sessionId + "/status"), ownerToken).content("{\"status\":\"SCHEDULED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"));
    }

    @Test
    void createTrainingSession_withInvalidBookingSlotId_isRejected() throws Exception {
        String ownerToken = registerOwner("session-badslot");
        String contactId = createContact(ownerToken, "Alex", "Client");

        mockMvc.perform(authed(post("/api/v1/training-sessions"), ownerToken)
                        .content(
                                """
                                {"contactId":"%s","bookingSlotId":"%s","startedAt":"2026-09-01T15:00:00Z","durationMinutes":45,
                                "sessionType":"IN_PERSON"}
                                """
                                        .formatted(contactId, java.util.UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    private String createContact(String token, String firstName, String lastName) throws Exception {
        MvcResult result = mockMvc
                .perform(authed(post("/api/v1/contacts"), token)
                        .content("{\"firstName\":\"%s\",\"lastName\":\"%s\"}".formatted(firstName, lastName)))
                .andExpect(status().isCreated())
                .andReturn();
        return readField(result, "data", "id");
    }

    private String createSession(String token, String contactId) throws Exception {
        MvcResult result = mockMvc
                .perform(authed(post("/api/v1/training-sessions"), token)
                        .content(
                                "{\"contactId\":\"%s\",\"startedAt\":\"2026-09-01T15:00:00Z\",\"durationMinutes\":45,\"sessionType\":\"IN_PERSON\"}"
                                        .formatted(contactId)))
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
