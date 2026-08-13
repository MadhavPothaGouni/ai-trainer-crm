package com.aitrainercrm.platform.contract;

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
 * End-to-end coverage for the Contract module - see V35's migration comment and Contract's javadoc
 * for the gap this fills (Quote is pre-close, Order/Invoice are transactional, nothing else tracks
 * the ongoing agreement). Covers full CRUD, the free (non-linear) status transition with signedAt
 * stamped once and never overwritten, and the per-organization contract number uniqueness
 * constraint - the same shape BookingLinkIntegrationTest already covers for slug.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ContractIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void createUpdateListAndDeleteContract_endToEnd() throws Exception {
        String ownerToken = registerOwner("contract-crud");
        String accountId = createAccount(ownerToken, "Acme Rockets");

        MvcResult createResult = mockMvc
                .perform(authed(post("/api/v1/contracts"), ownerToken)
                        .content(
                                """
                                {"accountId":"%s","contractNumber":"C-2001","title":"Annual Support",
                                "startDate":"2026-01-01","endDate":"2026-12-31","totalValue":12000.00,"autoRenew":false}
                                """
                                        .formatted(accountId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.contractNumber").value("C-2001"))
                .andExpect(jsonPath("$.data.signedAt").doesNotExist())
                .andReturn();
        String contractId = readField(createResult, "data", "id");
        assertThat(contractId).isNotBlank();

        mockMvc.perform(authed(put("/api/v1/contracts/" + contractId), ownerToken)
                        .content(
                                """
                                {"contractNumber":"C-2001","title":"Annual Support (Revised)",
                                "startDate":"2026-01-01","endDate":"2027-01-31","totalValue":15000.00,"autoRenew":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Annual Support (Revised)"))
                .andExpect(jsonPath("$.data.endDate").value("2027-01-31"));

        mockMvc.perform(authed(get("/api/v1/contracts"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(authed(delete("/api/v1/contracts/" + contractId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/contracts/" + contractId), ownerToken)).andExpect(status().isNotFound());
    }

    @Test
    void statusTransitionToActive_stampsSignedAtOnlyOnce() throws Exception {
        String ownerToken = registerOwner("contract-status");
        String accountId = createAccount(ownerToken, "Globex");
        String contractId = createContract(ownerToken, accountId, "C-3001");

        mockMvc.perform(authed(patch("/api/v1/contracts/" + contractId + "/status"), ownerToken).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.signedAt").exists());

        MvcResult afterFirstActivation = mockMvc.perform(authed(get("/api/v1/contracts/" + contractId), ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        String signedAtAfterFirstActivation = readField(afterFirstActivation, "data", "signedAt");

        // Moving away and back to ACTIVE - a legitimate correction, same restraint tickets.status
        // takes (see V14's migration comment) - must not overwrite the original signedAt.
        mockMvc.perform(authed(patch("/api/v1/contracts/" + contractId + "/status"), ownerToken).content("{\"status\":\"TERMINATED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(authed(patch("/api/v1/contracts/" + contractId + "/status"), ownerToken).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.signedAt").value(signedAtAfterFirstActivation));
    }

    @Test
    void createContract_duplicateNumberInSameOrganization_isRejected() throws Exception {
        String ownerToken = registerOwner("contract-dupe");
        String accountId = createAccount(ownerToken, "Initech");
        createContract(ownerToken, accountId, "C-4001");

        mockMvc.perform(authed(post("/api/v1/contracts"), ownerToken)
                        .content(
                                """
                                {"accountId":"%s","contractNumber":"C-4001","title":"Duplicate",
                                "startDate":"2026-01-01","endDate":"2026-12-31","totalValue":100,"autoRenew":false}
                                """
                                        .formatted(accountId)))
                .andExpect(status().isConflict());
    }

    private String createAccount(String token, String name) throws Exception {
        MvcResult result = mockMvc
                .perform(authed(post("/api/v1/accounts"), token).content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return readField(result, "data", "id");
    }

    private String createContract(String token, String accountId, String contractNumber) throws Exception {
        MvcResult result = mockMvc
                .perform(authed(post("/api/v1/contracts"), token)
                        .content(
                                """
                                {"accountId":"%s","contractNumber":"%s","title":"Support Agreement",
                                "startDate":"2026-01-01","endDate":"2026-12-31","totalValue":5000,"autoRenew":false}
                                """
                                        .formatted(accountId, contractNumber)))
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
