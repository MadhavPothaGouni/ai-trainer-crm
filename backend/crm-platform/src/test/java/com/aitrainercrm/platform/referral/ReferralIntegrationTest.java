package com.aitrainercrm.platform.referral;

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
 * End-to-end coverage for the Referral module - see V46's migration comment and Referral's
 * javadoc for the gap this fills. Covers full CRUD, the free status transition with
 * convertedContactId stamped once on entering CONVERTED, and the separate reward-issuance
 * endpoint requiring rewardAmount be set first.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ReferralIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void createUpdateListAndDeleteReferral_endToEnd() throws Exception {
        String ownerToken = registerOwner("referral-crud");
        String referrerId = createContact(ownerToken, "Jamie", "Client");

        MvcResult createResult = mockMvc
                .perform(authed(post("/api/v1/referrals"), ownerToken)
                        .content(
                                """
                                {"referrerContactId":"%s","referredName":"Alex Friend","referredEmail":"alex@example.com","rewardAmount":25.00}
                                """
                                        .formatted(referrerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.convertedContactId").doesNotExist())
                .andExpect(jsonPath("$.data.rewardIssuedAt").doesNotExist())
                .andReturn();
        String referralId = readField(createResult, "data", "id");
        assertThat(referralId).isNotBlank();

        mockMvc.perform(authed(put("/api/v1/referrals/" + referralId), ownerToken)
                        .content("{\"referredName\":\"Alex Friendly\",\"referredEmail\":\"alex@example.com\",\"rewardAmount\":30.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.referredName").value("Alex Friendly"))
                .andExpect(jsonPath("$.data.rewardAmount").value(30.00));

        mockMvc.perform(authed(get("/api/v1/referrals"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(authed(delete("/api/v1/referrals/" + referralId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/referrals/" + referralId), ownerToken)).andExpect(status().isNotFound());
    }

    @Test
    void statusTransitionToConverted_stampsConvertedContactIdOnlyOnce() throws Exception {
        String ownerToken = registerOwner("referral-convert");
        String referrerId = createContact(ownerToken, "Riley", "Client");
        String referralId = createReferral(ownerToken, referrerId, "Sam Prospect");
        String firstContactId = createContact(ownerToken, "Sam", "Prospect");
        String secondContactId = createContact(ownerToken, "Other", "Person");

        mockMvc.perform(authed(patch("/api/v1/referrals/" + referralId + "/status"), ownerToken)
                        .content("{\"status\":\"CONTACTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONTACTED"));

        mockMvc.perform(authed(patch("/api/v1/referrals/" + referralId + "/status"), ownerToken)
                        .content("{\"status\":\"CONVERTED\",\"convertedContactId\":\"%s\"}".formatted(firstContactId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONVERTED"))
                .andExpect(jsonPath("$.data.convertedContactId").value(firstContactId));

        // A later re-entry into CONVERTED with a different contact must not move the original.
        mockMvc.perform(authed(patch("/api/v1/referrals/" + referralId + "/status"), ownerToken)
                        .content("{\"status\":\"PENDING\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(authed(patch("/api/v1/referrals/" + referralId + "/status"), ownerToken)
                        .content("{\"status\":\"CONVERTED\",\"convertedContactId\":\"%s\"}".formatted(secondContactId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.convertedContactId").value(firstContactId));
    }

    @Test
    void issueReward_requiresRewardAmountAndStampsOnlyOnce() throws Exception {
        String ownerToken = registerOwner("referral-reward");
        String referrerId = createContact(ownerToken, "Morgan", "Client");

        MvcResult noRewardResult = mockMvc
                .perform(authed(post("/api/v1/referrals"), ownerToken).content("{\"referrerContactId\":\"%s\",\"referredName\":\"No Reward Yet\"}".formatted(referrerId)))
                .andExpect(status().isCreated())
                .andReturn();
        String noRewardReferralId = readField(noRewardResult, "data", "id");

        mockMvc.perform(authed(patch("/api/v1/referrals/" + noRewardReferralId + "/reward"), ownerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("REFERRAL_REWARD_NOT_SET"));

        String rewardedReferralId = createReferral(ownerToken, referrerId, "Has Reward");
        mockMvc.perform(authed(put("/api/v1/referrals/" + rewardedReferralId), ownerToken)
                        .content("{\"referredName\":\"Has Reward\",\"rewardAmount\":50.00}"))
                .andExpect(status().isOk());

        MvcResult firstIssueResult = mockMvc
                .perform(authed(patch("/api/v1/referrals/" + rewardedReferralId + "/reward"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rewardIssuedAt").exists())
                .andReturn();
        String rewardIssuedAt = readField(firstIssueResult, "data", "rewardIssuedAt");

        mockMvc.perform(authed(patch("/api/v1/referrals/" + rewardedReferralId + "/reward"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rewardIssuedAt").value(rewardIssuedAt));
    }

    private String createContact(String token, String firstName, String lastName) throws Exception {
        MvcResult result = mockMvc
                .perform(authed(post("/api/v1/contacts"), token)
                        .content("{\"firstName\":\"%s\",\"lastName\":\"%s\"}".formatted(firstName, lastName)))
                .andExpect(status().isCreated())
                .andReturn();
        return readField(result, "data", "id");
    }

    private String createReferral(String token, String referrerContactId, String referredName) throws Exception {
        MvcResult result = mockMvc
                .perform(authed(post("/api/v1/referrals"), token)
                        .content("{\"referrerContactId\":\"%s\",\"referredName\":\"%s\"}".formatted(referrerContactId, referredName)))
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
