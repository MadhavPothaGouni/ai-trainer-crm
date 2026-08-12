package com.aitrainercrm.platform.approval;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitrainercrm.platform.account.dto.CreateAccountRequest;
import com.aitrainercrm.platform.auth.dto.LoginRequest;
import com.aitrainercrm.platform.auth.dto.RegisterRequest;
import com.aitrainercrm.platform.support.AbstractIntegrationTest;
import com.aitrainercrm.platform.user.dto.CreateUserRequest;
import com.aitrainercrm.platform.user.entity.User;
import com.aitrainercrm.platform.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
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
 * End-to-end coverage for the Approval Workflow module - submission, sequential step gating
 * (a later step can't be decided before its predecessor), the two ways a chain can end (every
 * step approved vs. any single rejection killing the rest), requester-only cancellation, and the
 * named-approver visibility carve-out that lets a step's approver read a request they'd otherwise
 * have no owner-scope access to (see ApprovalRequestService's javadoc for that carve-out's
 * reasoning). Uses OPPORTUNITY as the relatedTo record throughout - QUOTE and ORDER go through
 * the identical validateRelatedTo switch, so one relatedTo type is enough to prove that path.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ApprovalRequestIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void submitApproveChainToCompletion_endToEnd() throws Exception {
        String ownerToken = registerOwner("appr-owner");
        String[] approverA = inviteAndLogin(ownerToken, "appr-a");
        String[] approverB = inviteAndLogin(ownerToken, "appr-b");
        String opportunityId = createOpportunity(ownerToken);

        MvcResult createResult = mockMvc
                .perform(authed(post("/api/v1/approval-requests"), ownerToken)
                        .content(createRequestJson(opportunityId, "Discount over 20%", approverA[0], approverB[0])))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.currentStepNumber").value(1))
                .andExpect(jsonPath("$.data.steps.length()").value(2))
                .andExpect(jsonPath("$.data.steps[0].stepNumber").value(1))
                .andExpect(jsonPath("$.data.steps[0].approverUserId").value(approverA[0]))
                .andExpect(jsonPath("$.data.steps[0].actionable").value(true))
                .andExpect(jsonPath("$.data.steps[1].stepNumber").value(2))
                .andExpect(jsonPath("$.data.steps[1].actionable").value(false))
                .andReturn();
        String requestId = readField(createResult, "data", "id");

        mockMvc.perform(authed(get("/api/v1/approval-requests"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        // Step 2's approver can't act before step 1 has decided - they're the right approver for
        // step 2, but it isn't the currently-actionable step yet, so this is a 409, not a 403.
        mockMvc.perform(authed(post("/api/v1/approval-requests/" + requestId + "/steps/2/approve"), approverB[1])
                        .content("{}"))
                .andExpect(status().isConflict());

        // Someone who isn't step 1's named approver can't decide it either, regardless of scope.
        mockMvc.perform(authed(post("/api/v1/approval-requests/" + requestId + "/steps/1/approve"), approverB[1])
                        .content("{}"))
                .andExpect(status().isForbidden());

        // Step 1's real approver signs off - chain advances to step 2, request still PENDING.
        mockMvc.perform(authed(post("/api/v1/approval-requests/" + requestId + "/steps/1/approve"), approverA[1])
                        .content("{\"comment\":\"Looks fine to me\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.currentStepNumber").value(2))
                .andExpect(jsonPath("$.data.steps[0].status").value("APPROVED"))
                .andExpect(jsonPath("$.data.steps[0].comment").value("Looks fine to me"));

        // Re-deciding an already-decided step is rejected, even by its own approver.
        mockMvc.perform(authed(post("/api/v1/approval-requests/" + requestId + "/steps/1/approve"), approverA[1])
                        .content("{}"))
                .andExpect(status().isConflict());

        // Step 2's approver finishes the chain - the whole request flips to APPROVED.
        mockMvc.perform(authed(post("/api/v1/approval-requests/" + requestId + "/steps/2/approve"), approverB[1])
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.decidedAt").isNotEmpty());

        // Once decided, a step no longer shows up in either approver's pending inbox.
        mockMvc.perform(authed(get("/api/v1/approval-requests/my-approvals"), approverA[1]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(authed(get("/api/v1/approval-requests/my-approvals"), approverB[1]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void rejectingAnyStep_killsTheWholeChainAndLeavesLaterStepsUntouched() throws Exception {
        String ownerToken = registerOwner("appr-reject-owner");
        String[] approverA = inviteAndLogin(ownerToken, "appr-reject-a");
        String[] approverB = inviteAndLogin(ownerToken, "appr-reject-b");
        String opportunityId = createOpportunity(ownerToken);

        String requestId = readField(
                mockMvc.perform(authed(post("/api/v1/approval-requests"), ownerToken)
                                .content(createRequestJson(opportunityId, "Custom SLA terms", approverA[0], approverB[0])))
                        .andExpect(status().isCreated())
                        .andReturn(),
                "data", "id");

        mockMvc.perform(authed(post("/api/v1/approval-requests/" + requestId + "/steps/1/reject"), approverA[1])
                        .content("{\"comment\":\"Terms too aggressive\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.steps[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.data.steps[1].status").value("PENDING"));

        // Step 2 never gets reached - the request is no longer PENDING at all, so its approver
        // can't act on it even though they were never the one who rejected anything.
        mockMvc.perform(authed(post("/api/v1/approval-requests/" + requestId + "/steps/2/approve"), approverB[1])
                        .content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    void cancel_onlyByRequesterAndOnlyWhilePending() throws Exception {
        String ownerToken = registerOwner("appr-cancel-owner");
        String[] approver = inviteAndLogin(ownerToken, "appr-cancel-a");
        String opportunityId = createOpportunity(ownerToken);

        String requestId = readField(
                mockMvc.perform(authed(post("/api/v1/approval-requests"), ownerToken)
                                .content(createRequestJson(opportunityId, "Extend payment terms", approver[0])))
                        .andExpect(status().isCreated())
                        .andReturn(),
                "data", "id");

        // The named approver isn't the requester, so they can't cancel it.
        mockMvc.perform(authed(patch("/api/v1/approval-requests/" + requestId + "/cancel"), approver[1]))
                .andExpect(status().isForbidden());

        mockMvc.perform(authed(patch("/api/v1/approval-requests/" + requestId + "/cancel"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        // Cancelling twice, or approving a step on an already-cancelled request, both fail - it's
        // no longer PENDING.
        mockMvc.perform(authed(patch("/api/v1/approval-requests/" + requestId + "/cancel"), ownerToken))
                .andExpect(status().isConflict());
        mockMvc.perform(authed(post("/api/v1/approval-requests/" + requestId + "/steps/1/approve"), approver[1])
                        .content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    void namedApprover_canReadRequestBeforeTheirTurn_butAnUninvolvedTeammateCannot() throws Exception {
        String ownerToken = registerOwner("appr-carveout-owner");
        String[] approverA = inviteAndLogin(ownerToken, "appr-carveout-a");
        String[] approverB = inviteAndLogin(ownerToken, "appr-carveout-b");
        String[] bystander = inviteAndLogin(ownerToken, "appr-carveout-bystander");
        String opportunityId = createOpportunity(ownerToken);

        String requestId = readField(
                mockMvc.perform(authed(post("/api/v1/approval-requests"), ownerToken)
                                .content(createRequestJson(opportunityId, "Bespoke pricing", approverA[0], approverB[0])))
                        .andExpect(status().isCreated())
                        .andReturn(),
                "data", "id");

        // Step 2's approver has no scope over the owner's requests (MEMBER only gets OWN/TEAM by
        // default, and this wasn't requested by them or a teammate on their team) - but being
        // named on the chain is enough on its own, regardless of when their step becomes actionable.
        mockMvc.perform(authed(get("/api/v1/approval-requests/" + requestId), approverB[1]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(requestId));

        // A teammate named on nothing gets the normal scope check, and fails it.
        mockMvc.perform(authed(get("/api/v1/approval-requests/" + requestId), bystander[1]))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_rejectsUnknownRelatedRecordDuplicateApproverAndCrossOrgApprover() throws Exception {
        String ownerToken = registerOwner("appr-validate-owner");
        String[] approverA = inviteAndLogin(ownerToken, "appr-validate-a");
        String opportunityId = createOpportunity(ownerToken);

        mockMvc.perform(authed(post("/api/v1/approval-requests"), ownerToken)
                        .content(createRequestJson(UUID.randomUUID().toString(), "Ghost record", approverA[0])))
                .andExpect(status().isNotFound());

        mockMvc.perform(authed(post("/api/v1/approval-requests"), ownerToken)
                        .content(createRequestJson(opportunityId, "Same approver twice", approverA[0], approverA[0])))
                .andExpect(status().isBadRequest());

        String otherOrgOwnerToken = registerOwner("appr-validate-otherorg");
        String otherOrgOwnerId =
                readField(mockMvc.perform(authed(get("/api/v1/users/me"), otherOrgOwnerToken)).andReturn(), "data", "id");
        mockMvc.perform(authed(post("/api/v1/approval-requests"), ownerToken)
                        .content(createRequestJson(opportunityId, "Cross-org approver", otherOrgOwnerId)))
                .andExpect(status().isNotFound());
    }

    private String createRequestJson(String relatedToId, String title, String... approverUserIds) {
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < approverUserIds.length; i++) {
            if (i > 0) ids.append(",");
            ids.append("\"").append(approverUserIds[i]).append("\"");
        }
        return "{\"relatedToType\":\"OPPORTUNITY\",\"relatedToId\":\"" + relatedToId + "\",\"title\":\"" + title
                + "\",\"approverUserIds\":[" + ids + "]}";
    }

    private String createOpportunity(String ownerToken) throws Exception {
        CreateAccountRequest createAccount = new CreateAccountRequest(
                "Globex Approval Co", null, null, null, null, null, null, null, null, null, null, null, null);
        String accountId = readField(
                mockMvc.perform(authed(post("/api/v1/accounts"), ownerToken).content(objectMapper.writeValueAsString(createAccount)))
                        .andExpect(status().isCreated())
                        .andReturn(),
                "data", "id");

        MvcResult opportunityResult = mockMvc
                .perform(authed(post("/api/v1/opportunities"), ownerToken)
                        .content("{\"accountId\":\"" + accountId + "\",\"name\":\"Renewal deal\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return readField(opportunityResult, "data", "id");
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
