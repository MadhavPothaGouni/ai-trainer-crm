package com.aitrainercrm.platform.leadscoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitrainercrm.platform.auth.dto.LoginRequest;
import com.aitrainercrm.platform.auth.dto.RegisterRequest;
import com.aitrainercrm.platform.lead.dto.CreateLeadRequest;
import com.aitrainercrm.platform.lead.entity.Lead;
import com.aitrainercrm.platform.support.AbstractIntegrationTest;
import com.aitrainercrm.platform.user.dto.CreateUserRequest;
import com.aitrainercrm.platform.user.entity.User;
import com.aitrainercrm.platform.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * End-to-end coverage for what an HTTP test can exercise directly: LeadScoringRule CRUD gated by
 * LEAD_SCORING_RULE:*:ORGANIZATION (a default MEMBER holds none of it, same as TerritoryRule), and
 * a real Lead create/update recomputing {@code Lead#score} via the real {@code @Async}
 * LeadScoringEngine - including the update-recompute path {@code TerritoryRuleIntegrationTest} has
 * no equivalent of, since TerritoryAssignmentListener never re-runs after creation. The engine's
 * full field-matching/summing branch coverage lives in {@code LeadScoringEngineTest} instead, the
 * same split {@code TerritoryRuleIntegrationTest}/{@code TerritoryAssignmentListenerTest} uses.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class LeadScoringRuleIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void ruleCrud_endToEnd() throws Exception {
        String ownerToken = registerOwner("scoring-crud-owner");

        MvcResult createResult = mockMvc
                .perform(authed(post("/api/v1/lead-scoring-rules"), ownerToken)
                        .content("{\"name\":\"Website leads\",\"matchField\":\"SOURCE\",\"matchOperator\":\"EQUALS\","
                                + "\"matchValue\":\"WEBSITE\",\"points\":10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Website leads"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.matchCount").value(0))
                .andReturn();
        String ruleId = readField(createResult, "data", "id");

        mockMvc.perform(authed(get("/api/v1/lead-scoring-rules"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(authed(get("/api/v1/lead-scoring-rules/" + ruleId), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.points").value(10));

        mockMvc.perform(authed(put("/api/v1/lead-scoring-rules/" + ruleId), ownerToken)
                        .content("{\"name\":\"Website leads v2\",\"matchField\":\"SOURCE\",\"matchOperator\":\"EQUALS\","
                                + "\"matchValue\":\"REFERRAL\",\"points\":25,\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Website leads v2"))
                .andExpect(jsonPath("$.data.points").value(25));

        mockMvc.perform(authed(delete("/api/v1/lead-scoring-rules/" + ruleId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/lead-scoring-rules/" + ruleId), ownerToken)).andExpect(status().isNotFound());
    }

    @Test
    void create_sourceMatchValueNotARealLeadSource_returns400() throws Exception {
        String ownerToken = registerOwner("scoring-badsource-owner");

        mockMvc.perform(authed(post("/api/v1/lead-scoring-rules"), ownerToken)
                        .content("{\"name\":\"Bad rule\",\"matchField\":\"SOURCE\",\"matchOperator\":\"EQUALS\","
                                + "\"matchValue\":\"NOT_A_SOURCE\",\"points\":10}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void memberWithoutPermission_forbiddenOnEveryEndpoint() throws Exception {
        String ownerToken = registerOwner("scoring-scope-owner");
        String[] rep = inviteAndLogin(ownerToken, "scoring-scope-rep");

        mockMvc.perform(authed(get("/api/v1/lead-scoring-rules"), rep[1])).andExpect(status().isForbidden());
        mockMvc.perform(authed(post("/api/v1/lead-scoring-rules"), rep[1])
                        .content("{\"name\":\"Bad\",\"matchField\":\"SOURCE\",\"matchOperator\":\"EQUALS\",\"matchValue\":\"WEBSITE\",\"points\":10}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void realLeadCreation_matchingActiveRule_recomputesScore_viaAsyncListener() throws Exception {
        String ownerToken = registerOwner("scoring-create-owner");

        mockMvc.perform(authed(post("/api/v1/lead-scoring-rules"), ownerToken)
                        .content("{\"name\":\"Website leads\",\"matchField\":\"SOURCE\",\"matchOperator\":\"EQUALS\","
                                + "\"matchValue\":\"WEBSITE\",\"points\":10}"))
                .andExpect(status().isCreated());
        mockMvc.perform(authed(post("/api/v1/lead-scoring-rules"), ownerToken)
                        .content("{\"name\":\"Director title\",\"matchField\":\"TITLE\",\"matchOperator\":\"CONTAINS\","
                                + "\"matchValue\":\"director\",\"points\":25}"))
                .andExpect(status().isCreated());

        CreateLeadRequest createLead = new CreateLeadRequest(
                "Ada", "Lovelace", null, null, "Analytical Engines Inc", "Director of Engineering", Lead.Source.WEBSITE, null, null);
        MvcResult leadResult = mockMvc
                .perform(authed(post("/api/v1/leads"), ownerToken).content(objectMapper.writeValueAsString(createLead)))
                .andExpect(status().isCreated())
                .andReturn();
        String leadId = readField(leadResult, "data", "id");

        // The engine runs @Async - poll for its effect rather than assuming timing (see
        // AbstractIntegrationTest#awaitAsync).
        int score = awaitAsync(
                () -> objectMapper
                        .readTree(mockMvc
                                .perform(authed(get("/api/v1/leads/" + leadId), ownerToken))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString())
                        .get("data")
                        .get("score")
                        .asInt(),
                s -> s == 35);
        assertThat(score).isEqualTo(35);
    }

    @Test
    void realLeadUpdate_scoreChangesAsMatchingRulesChange_unlikeTerritorysCreateOnlyBehavior() throws Exception {
        String ownerToken = registerOwner("scoring-update-owner");

        mockMvc.perform(authed(post("/api/v1/lead-scoring-rules"), ownerToken)
                        .content("{\"name\":\"Referral leads\",\"matchField\":\"SOURCE\",\"matchOperator\":\"EQUALS\","
                                + "\"matchValue\":\"REFERRAL\",\"points\":30}"))
                .andExpect(status().isCreated());

        CreateLeadRequest createLead =
                new CreateLeadRequest("Grace", "Hopper", null, null, "Compiler Co", null, Lead.Source.WEBSITE, null, null);
        MvcResult leadResult = mockMvc
                .perform(authed(post("/api/v1/leads"), ownerToken).content(objectMapper.writeValueAsString(createLead)))
                .andExpect(status().isCreated())
                .andReturn();
        String leadId = readField(leadResult, "data", "id");
        // Negative/baseline assertion (score should stay 0 - no rule matches a WEBSITE-source
        // lead) - there's no positive condition to poll for here, so this waits out the risk
        // window with a fixed sleep rather than awaitAsync, same reasoning as
        // CommissionIntegrationTest's re-update-must-not-duplicate check.
        Thread.sleep(300);

        mockMvc.perform(authed(get("/api/v1/leads/" + leadId), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(0));

        mockMvc.perform(authed(put("/api/v1/leads/" + leadId), ownerToken)
                        .content("{\"firstName\":\"Grace\",\"lastName\":\"Hopper\",\"companyName\":\"Compiler Co\","
                                + "\"source\":\"REFERRAL\"}"))
                .andExpect(status().isOk());

        int score = awaitAsync(
                () -> objectMapper
                        .readTree(mockMvc
                                .perform(authed(get("/api/v1/leads/" + leadId), ownerToken))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString())
                        .get("data")
                        .get("score")
                        .asInt(),
                s -> s == 30);
        assertThat(score).isEqualTo(30);
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

    /** @return {userId, accessToken} for a freshly invited MEMBER teammate in the caller's org. */
    private String[] inviteAndLogin(String ownerToken, String prefix) throws Exception {
        String email = "%s-%d@example.com".formatted(prefix, System.nanoTime());
        mockMvc.perform(authed(post("/api/v1/users"), ownerToken)
                        .content(objectMapper.writeValueAsString(new CreateUserRequest(email, "New", "Teammate", null))))
                .andExpect(status().isCreated());

        String password = "Str0ng!Passw0rd2";
        User teammate = userRepository.findByEmailAndDeletedAtIsNull(email.trim().toLowerCase()).orElseThrow();
        teammate.setPasswordHash(passwordEncoder.encode(password));
        userRepository.save(teammate);

        MvcResult loginResult = mockMvc
                .perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();
        return new String[] {readField(loginResult, "data", "userId"), readField(loginResult, "data", "accessToken")};
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
