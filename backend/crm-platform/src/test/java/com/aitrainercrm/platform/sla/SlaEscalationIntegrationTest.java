package com.aitrainercrm.platform.sla;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitrainercrm.platform.auth.dto.LoginRequest;
import com.aitrainercrm.platform.auth.dto.RegisterRequest;
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
 * End-to-end coverage for the parts of the SLA module an HTTP test can actually exercise quickly:
 * SlaPolicy CRUD (including the one-active-policy-per-priority conflict, on both create and
 * reactivate-via-update), escalateToUserId validation, and GET /ticket-sla/{id}'s two easily
 * reachable states ("nothing tracked" and "tracked, not yet breached"). Actual breach detection
 * and escalation-notification firing are covered by SlaEvaluationServiceTest instead - a policy's
 * minimum target is one real minute, which isn't something worth waiting on in a test suite that
 * runs on every push; SlaEvaluationServiceTest backdates a ticket's createdAt directly rather than
 * sleeping.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class SlaEscalationIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void policyCrud_endToEnd() throws Exception {
        String ownerToken = registerOwner("sla-crud-owner");
        String[] manager = inviteAndLogin(ownerToken, "sla-crud-manager");

        MvcResult createResult = mockMvc
                .perform(authed(post("/api/v1/sla-policies"), ownerToken)
                        .content("{\"name\":\"Urgent response\",\"priority\":\"URGENT\",\"responseTargetMinutes\":30,"
                                + "\"resolutionTargetMinutes\":240,\"escalateToUserId\":\"" + manager[0] + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Urgent response"))
                .andExpect(jsonPath("$.data.priority").value("URGENT"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andReturn();
        String policyId = readField(createResult, "data", "id");

        mockMvc.perform(authed(get("/api/v1/sla-policies"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(authed(get("/api/v1/sla-policies/" + policyId), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.responseTargetMinutes").value(30));

        mockMvc.perform(authed(put("/api/v1/sla-policies/" + policyId), ownerToken)
                        .content("{\"name\":\"Urgent response v2\",\"responseTargetMinutes\":15,"
                                + "\"resolutionTargetMinutes\":120,\"escalateToUserId\":\"" + manager[0] + "\",\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Urgent response v2"))
                .andExpect(jsonPath("$.data.responseTargetMinutes").value(15));

        mockMvc.perform(authed(delete("/api/v1/sla-policies/" + policyId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/sla-policies/" + policyId), ownerToken)).andExpect(status().isNotFound());
    }

    @Test
    void create_secondActivePolicyForSamePriority_returns409() throws Exception {
        String ownerToken = registerOwner("sla-conflict-owner");
        mockMvc.perform(authed(post("/api/v1/sla-policies"), ownerToken)
                        .content("{\"name\":\"High A\",\"priority\":\"HIGH\",\"responseTargetMinutes\":30,\"resolutionTargetMinutes\":240}"))
                .andExpect(status().isCreated());

        mockMvc.perform(authed(post("/api/v1/sla-policies"), ownerToken)
                        .content("{\"name\":\"High B\",\"priority\":\"HIGH\",\"responseTargetMinutes\":45,\"resolutionTargetMinutes\":300}"))
                .andExpect(status().isConflict());
    }

    @Test
    void update_reactivatingIntoAConflict_returns409() throws Exception {
        String ownerToken = registerOwner("sla-reactivate-owner");
        String policyAId = readField(
                mockMvc.perform(authed(post("/api/v1/sla-policies"), ownerToken)
                                .content("{\"name\":\"Medium A\",\"priority\":\"MEDIUM\",\"responseTargetMinutes\":60,\"resolutionTargetMinutes\":480}"))
                        .andExpect(status().isCreated())
                        .andReturn(),
                "data", "id");

        // Retire A so B can become the active MEDIUM policy.
        mockMvc.perform(authed(put("/api/v1/sla-policies/" + policyAId), ownerToken)
                        .content("{\"name\":\"Medium A\",\"responseTargetMinutes\":60,\"resolutionTargetMinutes\":480,\"active\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(authed(post("/api/v1/sla-policies"), ownerToken)
                        .content("{\"name\":\"Medium B\",\"priority\":\"MEDIUM\",\"responseTargetMinutes\":90,\"resolutionTargetMinutes\":600}"))
                .andExpect(status().isCreated());

        // Reactivating A now collides with B, which is currently the active MEDIUM policy.
        mockMvc.perform(authed(put("/api/v1/sla-policies/" + policyAId), ownerToken)
                        .content("{\"name\":\"Medium A\",\"responseTargetMinutes\":60,\"resolutionTargetMinutes\":480,\"active\":true}"))
                .andExpect(status().isConflict());
    }

    @Test
    void create_withEscalateToUserFromAnotherOrganization_returns404() throws Exception {
        String ownerToken = registerOwner("sla-crossorg-owner");
        String otherOrgOwnerToken = registerOwner("sla-crossorg-other");
        String otherOrgOwnerId =
                readField(mockMvc.perform(authed(get("/api/v1/users/me"), otherOrgOwnerToken)).andReturn(), "data", "id");

        mockMvc.perform(authed(post("/api/v1/sla-policies"), ownerToken)
                        .content("{\"name\":\"Low\",\"priority\":\"LOW\",\"responseTargetMinutes\":120,\"resolutionTargetMinutes\":1440,"
                                + "\"escalateToUserId\":\"" + otherOrgOwnerId + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void ticketSla_noMatchingPolicy_returnsNullDataWithExplanation() throws Exception {
        String ownerToken = registerOwner("sla-notracked-owner");
        String ticketId = createTicket(ownerToken, "URGENT");

        mockMvc.perform(authed(get("/api/v1/ticket-sla/" + ticketId), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.message").value("No active SLA policy covers this ticket's priority"));
    }

    @Test
    void ticketSla_withMatchingPolicyNotYetBreached_reportsTrackedNoBreach() throws Exception {
        String ownerToken = registerOwner("sla-tracked-owner");
        mockMvc.perform(authed(post("/api/v1/sla-policies"), ownerToken)
                        .content("{\"name\":\"Low - generous\",\"priority\":\"LOW\",\"responseTargetMinutes\":999999,\"resolutionTargetMinutes\":999999}"))
                .andExpect(status().isCreated());
        String ticketId = createTicket(ownerToken, "LOW");

        mockMvc.perform(authed(get("/api/v1/ticket-sla/" + ticketId), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.responseBreached").value(false))
                .andExpect(jsonPath("$.data.resolutionBreached").value(false))
                .andExpect(jsonPath("$.data.responseDueAt").isNotEmpty())
                .andExpect(jsonPath("$.data.resolutionDueAt").isNotEmpty());
    }

    @Test
    void ticketSla_callerWithoutReadScopeOnTheTicket_returns403() throws Exception {
        String ownerToken = registerOwner("sla-scope-owner");
        String[] teammate = inviteAndLogin(ownerToken, "sla-scope-teammate");
        // Owner-owned ticket - the teammate's default MEMBER role only grants TICKET:READ:OWN,
        // and this ticket isn't theirs.
        String ticketId = createTicket(ownerToken, "MEDIUM");

        mockMvc.perform(authed(get("/api/v1/ticket-sla/" + ticketId), teammate[1])).andExpect(status().isForbidden());
    }

    private String createTicket(String ownerToken, String priority) throws Exception {
        MvcResult result = mockMvc
                .perform(authed(post("/api/v1/tickets"), ownerToken)
                        .content("{\"subject\":\"Something is on fire\",\"priority\":\"" + priority + "\"}"))
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
