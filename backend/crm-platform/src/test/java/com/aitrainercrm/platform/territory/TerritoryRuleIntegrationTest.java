package com.aitrainercrm.platform.territory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * End-to-end coverage for the parts an HTTP test can exercise directly: TerritoryRule CRUD
 * (including the "exactly one assignment target" and field/resource-pairing validation), plus a
 * real Lead creation auto-assigning via {@code TerritoryAssignmentListener} - both the direct-user
 * path and, across two leads, the team round-robin actually advancing. The listener's full
 * matching/round-robin branch coverage (case-insensitivity, wrap-around, a stale cursor, an empty
 * team) lives in {@code TerritoryAssignmentListenerTest} instead, the same split
 * {@code SlaEscalationIntegrationTest}/{@code SlaEvaluationServiceTest} uses - an HTTP test isn't
 * the right place to force every branch of an algorithm that only needs one dependency mocked out
 * to reach.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class TerritoryRuleIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void ruleCrud_endToEnd() throws Exception {
        String ownerToken = registerOwner("territory-crud-owner");
        String[] rep = inviteAndLogin(ownerToken, "territory-crud-rep");

        MvcResult createResult = mockMvc
                .perform(authed(post("/api/v1/territory-rules"), ownerToken)
                        .content("{\"name\":\"Website leads to Rep\",\"targetResource\":\"LEAD\",\"matchField\":\"SOURCE\","
                                + "\"matchOperator\":\"EQUALS\",\"matchValue\":\"WEBSITE\",\"priority\":10,\"assignToUserId\":\""
                                + rep[0] + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Website leads to Rep"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.matchCount").value(0))
                .andReturn();
        String ruleId = readField(createResult, "data", "id");

        mockMvc.perform(authed(get("/api/v1/territory-rules"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(authed(get("/api/v1/territory-rules/" + ruleId), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matchValue").value("WEBSITE"));

        mockMvc.perform(authed(put("/api/v1/territory-rules/" + ruleId), ownerToken)
                        .content("{\"name\":\"Website leads to Rep v2\",\"matchField\":\"SOURCE\",\"matchOperator\":\"EQUALS\","
                                + "\"matchValue\":\"REFERRAL\",\"priority\":20,\"assignToUserId\":\"" + rep[0] + "\",\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Website leads to Rep v2"))
                .andExpect(jsonPath("$.data.matchValue").value("REFERRAL"));

        mockMvc.perform(authed(delete("/api/v1/territory-rules/" + ruleId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/territory-rules/" + ruleId), ownerToken)).andExpect(status().isNotFound());
    }

    @Test
    void create_withBothAssignmentTargetsSet_returns400() throws Exception {
        String ownerToken = registerOwner("territory-bothtargets-owner");
        String[] rep = inviteAndLogin(ownerToken, "territory-bothtargets-rep");
        String teamId = createTeam(ownerToken, "EMEA");

        mockMvc.perform(authed(post("/api/v1/territory-rules"), ownerToken)
                        .content("{\"name\":\"Bad rule\",\"targetResource\":\"LEAD\",\"matchField\":\"SOURCE\","
                                + "\"matchOperator\":\"EQUALS\",\"matchValue\":\"WEBSITE\",\"priority\":10,"
                                + "\"assignToUserId\":\"" + rep[0] + "\",\"assignToTeamId\":\"" + teamId + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_withNeitherAssignmentTargetSet_returns400() throws Exception {
        String ownerToken = registerOwner("territory-notargets-owner");

        mockMvc.perform(authed(post("/api/v1/territory-rules"), ownerToken)
                        .content("{\"name\":\"Bad rule\",\"targetResource\":\"LEAD\",\"matchField\":\"SOURCE\","
                                + "\"matchOperator\":\"EQUALS\",\"matchValue\":\"WEBSITE\",\"priority\":10}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_matchFieldNotValidForTargetResource_returns400() throws Exception {
        String ownerToken = registerOwner("territory-badfield-owner");
        String[] rep = inviteAndLogin(ownerToken, "territory-badfield-rep");

        // INDUSTRY is an ACCOUNT-only field - invalid for a LEAD rule.
        mockMvc.perform(authed(post("/api/v1/territory-rules"), ownerToken)
                        .content("{\"name\":\"Bad rule\",\"targetResource\":\"LEAD\",\"matchField\":\"INDUSTRY\","
                                + "\"matchOperator\":\"EQUALS\",\"matchValue\":\"Software\",\"priority\":10,\"assignToUserId\":\""
                                + rep[0] + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_sourceMatchValueNotARealLeadSource_returns400() throws Exception {
        String ownerToken = registerOwner("territory-badsource-owner");
        String[] rep = inviteAndLogin(ownerToken, "territory-badsource-rep");

        mockMvc.perform(authed(post("/api/v1/territory-rules"), ownerToken)
                        .content("{\"name\":\"Bad rule\",\"targetResource\":\"LEAD\",\"matchField\":\"SOURCE\","
                                + "\"matchOperator\":\"EQUALS\",\"matchValue\":\"NOT_A_SOURCE\",\"priority\":10,\"assignToUserId\":\""
                                + rep[0] + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void realLeadCreation_matchesActiveRule_autoAssignsToDirectUser_viaAsyncListener() throws Exception {
        String ownerToken = registerOwner("territory-direct-owner");
        String[] rep = inviteAndLogin(ownerToken, "territory-direct-rep");

        mockMvc.perform(authed(post("/api/v1/territory-rules"), ownerToken)
                        .content("{\"name\":\"Website leads to Rep\",\"targetResource\":\"LEAD\",\"matchField\":\"SOURCE\","
                                + "\"matchOperator\":\"EQUALS\",\"matchValue\":\"WEBSITE\",\"priority\":10,\"assignToUserId\":\""
                                + rep[0] + "\"}"))
                .andExpect(status().isCreated());

        CreateLeadRequest createLead =
                new CreateLeadRequest("Ada", "Lovelace", null, null, "Analytical Engines Inc", null, Lead.Source.WEBSITE, null, null);
        MvcResult leadResult = mockMvc
                .perform(authed(post("/api/v1/leads"), ownerToken).content(objectMapper.writeValueAsString(createLead)))
                .andExpect(status().isCreated())
                .andReturn();
        String leadId = readField(leadResult, "data", "id");

        // --- The listener runs @Async - wait for it rather than assuming timing (same pattern WorkflowIntegrationTest/WebhookIntegrationTest use) ---
        Thread.sleep(300);

        mockMvc.perform(authed(get("/api/v1/leads/" + leadId), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ownerId").value(rep[0]));
    }

    @Test
    void realLeadCreation_noMatchingRule_ownerStaysTheCreator() throws Exception {
        String ownerToken = registerOwner("territory-nomatch-owner");
        String[] rep = inviteAndLogin(ownerToken, "territory-nomatch-rep");
        String ownerId = readField(mockMvc.perform(authed(get("/api/v1/users/me"), ownerToken)).andReturn(), "data", "id");

        mockMvc.perform(authed(post("/api/v1/territory-rules"), ownerToken)
                        .content("{\"name\":\"Referral leads to Rep\",\"targetResource\":\"LEAD\",\"matchField\":\"SOURCE\","
                                + "\"matchOperator\":\"EQUALS\",\"matchValue\":\"REFERRAL\",\"priority\":10,\"assignToUserId\":\""
                                + rep[0] + "\"}"))
                .andExpect(status().isCreated());

        CreateLeadRequest createLead =
                new CreateLeadRequest("Grace", "Hopper", null, null, "Compiler Co", null, Lead.Source.WEBSITE, null, null);
        MvcResult leadResult = mockMvc
                .perform(authed(post("/api/v1/leads"), ownerToken).content(objectMapper.writeValueAsString(createLead)))
                .andExpect(status().isCreated())
                .andReturn();
        String leadId = readField(leadResult, "data", "id");

        Thread.sleep(300);

        mockMvc.perform(authed(get("/api/v1/leads/" + leadId), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ownerId").value(ownerId));
    }

    @Test
    void realLeadCreation_teamAssignment_roundRobinsAcrossTwoNewLeads() throws Exception {
        String ownerToken = registerOwner("territory-roundrobin-owner");
        String[] repOne = inviteAndLogin(ownerToken, "territory-roundrobin-rep1");
        String[] repTwo = inviteAndLogin(ownerToken, "territory-roundrobin-rep2");
        String teamId = createTeam(ownerToken, "Inbound");
        putOnTeam(ownerToken, repOne[0], teamId);
        putOnTeam(ownerToken, repTwo[0], teamId);

        mockMvc.perform(authed(post("/api/v1/territory-rules"), ownerToken)
                        .content("{\"name\":\"Event leads to Inbound team\",\"targetResource\":\"LEAD\",\"matchField\":\"SOURCE\","
                                + "\"matchOperator\":\"EQUALS\",\"matchValue\":\"EVENT\",\"priority\":10,\"assignToTeamId\":\""
                                + teamId + "\"}"))
                .andExpect(status().isCreated());

        CreateLeadRequest leadOne =
                new CreateLeadRequest("Alan", "Turing", null, null, "Enigma Ltd", null, Lead.Source.EVENT, null, null);
        MvcResult leadOneResult = mockMvc
                .perform(authed(post("/api/v1/leads"), ownerToken).content(objectMapper.writeValueAsString(leadOne)))
                .andExpect(status().isCreated())
                .andReturn();
        String leadOneId = readField(leadOneResult, "data", "id");
        Thread.sleep(300);

        CreateLeadRequest leadTwo =
                new CreateLeadRequest("Katherine", "Johnson", null, null, "NASA", null, Lead.Source.EVENT, null, null);
        MvcResult leadTwoResult = mockMvc
                .perform(authed(post("/api/v1/leads"), ownerToken).content(objectMapper.writeValueAsString(leadTwo)))
                .andExpect(status().isCreated())
                .andReturn();
        String leadTwoId = readField(leadTwoResult, "data", "id");
        Thread.sleep(300);

        MvcResult ownerOneResult =
                mockMvc.perform(authed(get("/api/v1/leads/" + leadOneId), ownerToken)).andExpect(status().isOk()).andReturn();
        MvcResult ownerTwoResult =
                mockMvc.perform(authed(get("/api/v1/leads/" + leadTwoId), ownerToken)).andExpect(status().isOk()).andReturn();
        String firstOwner = readField(ownerOneResult, "data", "ownerId");
        String secondOwner = readField(ownerTwoResult, "data", "ownerId");

        // Both leads landed on a real member of the team, and round-robin gave them to different people.
        assertThat(firstOwner).isIn(repOne[0], repTwo[0]);
        assertThat(secondOwner).isIn(repOne[0], repTwo[0]);
        assertThat(secondOwner).isNotEqualTo(firstOwner);
    }

    private String createTeam(String ownerToken, String name) throws Exception {
        MvcResult result = mockMvc
                .perform(authed(post("/api/v1/teams"), ownerToken).content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return readField(result, "data", "id");
    }

    private void putOnTeam(String ownerToken, String userId, String teamId) throws Exception {
        mockMvc.perform(authed(patch("/api/v1/users/" + userId + "/team"), ownerToken).content("{\"teamId\":\"" + teamId + "\"}"))
                .andExpect(status().isOk());
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
