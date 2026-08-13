package com.aitrainercrm.platform.commission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitrainercrm.platform.account.dto.CreateAccountRequest;
import com.aitrainercrm.platform.auth.dto.LoginRequest;
import com.aitrainercrm.platform.auth.dto.RegisterRequest;
import com.aitrainercrm.platform.opportunity.dto.CreateOpportunityRequest;
import com.aitrainercrm.platform.support.AbstractIntegrationTest;
import com.aitrainercrm.platform.user.dto.CreateUserRequest;
import com.aitrainercrm.platform.user.entity.User;
import com.aitrainercrm.platform.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
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
 * End-to-end coverage for what only real HTTP + real Postgres + the real async listener can pin
 * down: CommissionPlan CRUD gated by COMMISSION_PLAN:*:ORGANIZATION, a real Opportunity closing
 * CLOSED_WON triggering {@code CommissionEngine} to create exactly one CommissionRecord with the
 * expected computed amount, the individual-plan-beats-team-plan resolution actually winning
 * through the API (not just CommissionEngineTest's mocks), the PENDING->APPROVED->PAID walk, an
 * illegal transition rejected with 400, and the self-service /mine endpoint. {@code
 * CommissionPlanServiceTest}/{@code CommissionRecordServiceTest}/{@code CommissionEngineTest}
 * cover validation and computation edge cases with mocks; this class only asserts on behavior that
 * requires a real database round trip and a real fired {@code RecordUpdated} event.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CommissionIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void commissionPlanCrud_endToEnd() throws Exception {
        String ownerToken = registerOwner("commission-plan-crud-owner");
        String[] rep = inviteAndLogin(ownerToken, "commission-plan-crud-rep");

        String planId = createIndividualPlan(ownerToken, "Standard", rep[0], "PERCENTAGE", "5.00");

        mockMvc.perform(authed(get("/api/v1/commission-plans"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1));

        mockMvc.perform(authed(put("/api/v1/commission-plans/" + planId), ownerToken)
                        .content("{\"name\":\"Standard v2\",\"ownerUserId\":\"" + rep[0]
                                + "\",\"rateType\":\"PERCENTAGE\",\"rate\":6.00,\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Standard v2"))
                .andExpect(jsonPath("$.data.rate").value(6.00));

        mockMvc.perform(authed(delete("/api/v1/commission-plans/" + planId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/commission-plans/" + planId), ownerToken)).andExpect(status().isNotFound());
    }

    @Test
    void createCommissionPlan_neitherOwnerNorTeam_returns400() throws Exception {
        String ownerToken = registerOwner("commission-plan-invalid-owner");

        mockMvc.perform(authed(post("/api/v1/commission-plans"), ownerToken)
                        .content("{\"name\":\"Bad\",\"rateType\":\"PERCENTAGE\",\"rate\":5.00}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void closingAnOpportunity_createsExactlyOneCommissionRecord_viaAsyncListener() throws Exception {
        String ownerToken = registerOwner("commission-close-owner");
        String[] rep = inviteAndLogin(ownerToken, "commission-close-rep");
        createIndividualPlan(ownerToken, "Standard", rep[0], "PERCENTAGE", "10.00");

        UUID accountId = createAccount(rep[1], "Acme Co");
        String dealId = createOpportunity(rep[1], accountId, new BigDecimal("2000.00"));

        mockMvc.perform(authed(patch("/api/v1/opportunities/" + dealId + "/stage"), rep[1]).content("{\"stage\":\"CLOSED_WON\"}"))
                .andExpect(status().isOk());

        // CommissionEngine runs @Async - poll for its effect rather than guessing at a fixed sleep
        // (see AbstractIntegrationTest#awaitAsync).
        JsonNode mine = awaitAsync(
                () -> objectMapper
                        .readTree(mockMvc
                                .perform(authed(get("/api/v1/commission-records/mine"), rep[1]))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString())
                        .get("data"),
                data -> data.size() >= 1);
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).get("dealAmount").decimalValue()).isEqualByComparingTo("2000.00");
        assertThat(mine.get(0).get("commissionAmount").decimalValue()).isEqualByComparingTo("200.00");
        assertThat(mine.get(0).get("status").asText()).isEqualTo("PENDING");

        // Re-updating the same already-closed deal must not create a second record - the same PUT
        // fires another RecordUpdated event, and CommissionEngine's existence check must no-op it.
        mockMvc.perform(authed(put("/api/v1/opportunities/" + dealId), rep[1])
                        .content("{\"accountId\":\"" + accountId + "\",\"name\":\"Acme Co Renewal\",\"amount\":2000.00}"))
                .andExpect(status().isOk());
        // Negative assertion (no second record should appear) - there's no positive signal to poll
        // for here, so this waits out the risk window with a fixed sleep rather than awaitAsync.
        Thread.sleep(500);

        MvcResult mineAgain =
                mockMvc.perform(authed(get("/api/v1/commission-records/mine"), rep[1])).andExpect(status().isOk()).andReturn();
        JsonNode mineAgainData = objectMapper.readTree(mineAgain.getResponse().getContentAsString()).get("data");
        assertThat(mineAgainData).hasSize(1);
    }

    @Test
    void individualPlan_winsOverTeamPlan_viaRealApi() throws Exception {
        String ownerToken = registerOwner("commission-priority-owner");
        String[] rep = inviteAndLogin(ownerToken, "commission-priority-rep");

        MvcResult teamResult = mockMvc.perform(authed(post("/api/v1/teams"), ownerToken).content("{\"name\":\"Closers\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String teamId = readField(teamResult, "data", "id");
        mockMvc.perform(authed(patch("/api/v1/users/" + rep[0] + "/team"), ownerToken).content("{\"teamId\":\"" + teamId + "\"}"))
                .andExpect(status().isOk());

        createTeamPlan(ownerToken, "Team Plan", teamId, "FLAT_PER_DEAL", "50.00");
        createIndividualPlan(ownerToken, "Individual Plan", rep[0], "FLAT_PER_DEAL", "300.00");

        UUID accountId = createAccount(rep[1], "Priority Co");
        String dealId = createOpportunity(rep[1], accountId, new BigDecimal("1000.00"));
        mockMvc.perform(authed(patch("/api/v1/opportunities/" + dealId + "/stage"), rep[1]).content("{\"stage\":\"CLOSED_WON\"}"))
                .andExpect(status().isOk());

        JsonNode mine = awaitAsync(
                () -> objectMapper
                        .readTree(mockMvc
                                .perform(authed(get("/api/v1/commission-records/mine"), rep[1]))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString())
                        .get("data"),
                data -> data.size() >= 1);
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).get("commissionAmount").decimalValue()).isEqualByComparingTo("300.00");
    }

    @Test
    void statusTransition_pendingApprovedPaid_walkSucceeds_andIllegalSkipReturns400() throws Exception {
        String ownerToken = registerOwner("commission-status-owner");
        String[] rep = inviteAndLogin(ownerToken, "commission-status-rep");
        createIndividualPlan(ownerToken, "Standard", rep[0], "PERCENTAGE", "10.00");

        UUID accountId = createAccount(rep[1], "Status Co");
        String dealId = createOpportunity(rep[1], accountId, new BigDecimal("500.00"));
        mockMvc.perform(authed(patch("/api/v1/opportunities/" + dealId + "/stage"), rep[1]).content("{\"stage\":\"CLOSED_WON\"}"))
                .andExpect(status().isOk());

        JsonNode listContent = awaitAsync(
                () -> objectMapper
                        .readTree(mockMvc
                                .perform(authed(get("/api/v1/commission-records"), ownerToken))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString())
                        .get("data")
                        .get("content"),
                data -> data.size() >= 1);
        assertThat(listContent).hasSize(1);
        String recordId = listContent.get(0).get("id").asText();
        assertThat(recordId).isNotEmpty();

        mockMvc.perform(authed(patch("/api/v1/commission-records/" + recordId + "/status"), ownerToken)
                        .content("{\"status\":\"PAID\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(authed(patch("/api/v1/commission-records/" + recordId + "/status"), ownerToken)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        mockMvc.perform(authed(patch("/api/v1/commission-records/" + recordId + "/status"), ownerToken)
                        .content("{\"status\":\"PAID\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.paidAt").isNotEmpty());
    }

    @Test
    void repWithoutCommissionPermission_cannotListAllRecords() throws Exception {
        String ownerToken = registerOwner("commission-permission-owner");
        String[] rep = inviteAndLogin(ownerToken, "commission-permission-rep");

        mockMvc.perform(authed(get("/api/v1/commission-records"), rep[1])).andExpect(status().isForbidden());
    }

    private String createIndividualPlan(String ownerToken, String name, String ownerUserId, String rateType, String rate)
            throws Exception {
        MvcResult result = mockMvc
                .perform(authed(post("/api/v1/commission-plans"), ownerToken)
                        .content("{\"name\":\"" + name + "\",\"ownerUserId\":\"" + ownerUserId + "\",\"rateType\":\"" + rateType
                                + "\",\"rate\":" + rate + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        return readField(result, "data", "id");
    }

    private String createTeamPlan(String ownerToken, String name, String teamId, String rateType, String rate) throws Exception {
        MvcResult result = mockMvc
                .perform(authed(post("/api/v1/commission-plans"), ownerToken)
                        .content("{\"name\":\"" + name + "\",\"teamId\":\"" + teamId + "\",\"rateType\":\"" + rateType + "\",\"rate\":"
                                + rate + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        return readField(result, "data", "id");
    }

    private UUID createAccount(String token, String name) throws Exception {
        MvcResult result = mockMvc
                .perform(authed(post("/api/v1/accounts"), token)
                        .content(objectMapper.writeValueAsString(
                                new CreateAccountRequest(name, null, null, null, null, null, null, null, null, null, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readField(result, "data", "id"));
    }

    private String createOpportunity(String token, UUID accountId, BigDecimal amount) throws Exception {
        MvcResult result = mockMvc
                .perform(authed(post("/api/v1/opportunities"), token)
                        .content(objectMapper.writeValueAsString(
                                new CreateOpportunityRequest(accountId, null, "Deal", amount, null, null, null, null))))
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
