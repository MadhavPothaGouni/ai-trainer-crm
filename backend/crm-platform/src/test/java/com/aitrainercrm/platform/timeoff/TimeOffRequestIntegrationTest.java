package com.aitrainercrm.platform.timeoff;

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
 * End-to-end coverage for the Time-Off Request module - see V49's migration comment and
 * TimeOffRequest's javadoc for the gap this fills. Covers full CRUD plus the free status
 * transition with approvedAt stamped once on entering APPROVED, mirroring
 * ReferralIntegrationTest's structure.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class TimeOffRequestIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void createUpdateListAndDeleteTimeOffRequest_endToEnd() throws Exception {
        String ownerToken = registerOwner("timeoff-crud");

        MvcResult createResult = mockMvc
                .perform(authed(post("/api/v1/time-off-requests"), ownerToken)
                        .content(
                                """
                                {"startDate":"2026-09-01","endDate":"2026-09-05","type":"VACATION","reason":"Family trip"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.type").value("VACATION"))
                .andExpect(jsonPath("$.data.approvedAt").doesNotExist())
                .andReturn();
        String timeOffRequestId = readField(createResult, "data", "id");
        assertThat(timeOffRequestId).isNotBlank();

        mockMvc.perform(authed(put("/api/v1/time-off-requests/" + timeOffRequestId), ownerToken)
                        .content(
                                """
                                {"startDate":"2026-09-02","endDate":"2026-09-06","type":"VACATION","reason":"Family trip, extended"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.endDate").value("2026-09-06"))
                .andExpect(jsonPath("$.data.reason").value("Family trip, extended"));

        mockMvc.perform(authed(get("/api/v1/time-off-requests"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(authed(delete("/api/v1/time-off-requests/" + timeOffRequestId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/time-off-requests/" + timeOffRequestId), ownerToken)).andExpect(status().isNotFound());
    }

    @Test
    void statusTransitionToApproved_stampsApprovedAtOnlyOnce() throws Exception {
        String ownerToken = registerOwner("timeoff-approve");
        String timeOffRequestId = createTimeOffRequest(ownerToken, "2026-10-01", "2026-10-03");

        mockMvc.perform(authed(patch("/api/v1/time-off-requests/" + timeOffRequestId + "/status"), ownerToken)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.approvedAt").exists());

        MvcResult firstApproveResult = mockMvc
                .perform(authed(get("/api/v1/time-off-requests/" + timeOffRequestId), ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        String approvedAt = readField(firstApproveResult, "data", "approvedAt");

        // Moving away and back to APPROVED must not restamp approvedAt.
        mockMvc.perform(authed(patch("/api/v1/time-off-requests/" + timeOffRequestId + "/status"), ownerToken)
                        .content("{\"status\":\"DENIED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DENIED"))
                .andExpect(jsonPath("$.data.approvedAt").value(approvedAt));

        mockMvc.perform(authed(patch("/api/v1/time-off-requests/" + timeOffRequestId + "/status"), ownerToken)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvedAt").value(approvedAt));
    }

    private String createTimeOffRequest(String token, String startDate, String endDate) throws Exception {
        MvcResult result = mockMvc
                .perform(authed(post("/api/v1/time-off-requests"), token)
                        .content("{\"startDate\":\"%s\",\"endDate\":\"%s\",\"type\":\"VACATION\"}".formatted(startDate, endDate)))
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
