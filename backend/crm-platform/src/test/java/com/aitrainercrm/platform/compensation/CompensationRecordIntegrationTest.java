package com.aitrainercrm.platform.compensation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * End-to-end coverage for compensation records - see V57's migration comment. The owner (registered
 * org owner) both manages and is the staff member being paid here for simplicity, since creating a
 * second real user via /api/v1/users is exercised more thoroughly by other modules' teammate tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CompensationRecordIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void compensationRecordLifecycle_endToEnd() throws Exception {
        String ownerToken = registerOwner("compensation-crud");
        String ownerId = readField(decodeMe(ownerToken), "data", "id");

        MvcResult recordResult = mockMvc
                .perform(authed(post("/api/v1/compensation-records"), ownerToken)
                        .content(("{\"staffUserId\":\"%s\",\"payPeriodStart\":\"2026-08-01\",\"payPeriodEnd\":\"2026-08-15\","
                                        + "\"hoursWorked\":40,\"hourlyRate\":25,\"commissionAmount\":100,\"bonusAmount\":50}")
                                .formatted(ownerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.totalAmount").value(1150.00))
                .andReturn();
        String recordId = readField(recordResult, "data", "id");
        assertThat(recordId).isNotBlank();

        // An invalid pay period (end before start) is rejected.
        mockMvc.perform(authed(post("/api/v1/compensation-records"), ownerToken)
                        .content(("{\"staffUserId\":\"%s\",\"payPeriodStart\":\"2026-08-15\",\"payPeriodEnd\":\"2026-08-01\","
                                        + "\"hoursWorked\":40,\"hourlyRate\":25}")
                                .formatted(ownerId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMPENSATION_RECORD_INVALID_PERIOD"));

        mockMvc.perform(authed(patch("/api/v1/compensation-records/" + recordId + "/status"), ownerToken).content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        MvcResult paidResult = mockMvc
                .perform(authed(patch("/api/v1/compensation-records/" + recordId + "/status"), ownerToken).content("{\"status\":\"PAID\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.paidAt").exists())
                .andReturn();
        String paidAt = readField(paidResult, "data", "paidAt");

        // A later correction back through DRAFT and to PAID again must not move paidAt.
        mockMvc.perform(authed(patch("/api/v1/compensation-records/" + recordId + "/status"), ownerToken).content("{\"status\":\"DRAFT\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(authed(patch("/api/v1/compensation-records/" + recordId + "/status"), ownerToken).content("{\"status\":\"PAID\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paidAt").value(paidAt));

        mockMvc.perform(authed(get("/api/v1/compensation-records"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(authed(delete("/api/v1/compensation-records/" + recordId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/compensation-records/" + recordId), ownerToken)).andExpect(status().isNotFound());
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

    private MvcResult decodeMe(String accessToken) throws Exception {
        return mockMvc.perform(authed(get("/api/v1/users/me"), accessToken)).andExpect(status().isOk()).andReturn();
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
