package com.aitrainercrm.platform.salesgoals;

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
import java.time.LocalDate;
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
 * End-to-end coverage for what an HTTP test can exercise directly: SalesGoal CRUD gated by
 * SALES_GOAL:*:ORGANIZATION, the exactly-one-target and invalid-period 400s, and - the module's
 * central claim - a real won Opportunity making a real goal's progress move, for both an
 * individually-assigned goal and a team goal, read back through both the admin GET and the
 * self-service {@code /mine} endpoint a MEMBER with no SALES_GOAL permission at all can still
 * call. {@code SalesGoalServiceTest} covers the progress-math branches (metric selection, percent
 * rounding, zero-target guard, empty-team short-circuit) with mocks instead.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class SalesGoalIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void goalCrud_endToEnd() throws Exception {
        String ownerToken = registerOwner("goals-crud-owner");
        String[] rep = inviteAndLogin(ownerToken, "goals-crud-rep");

        MvcResult createResult = mockMvc
                .perform(authed(post("/api/v1/sales-goals"), ownerToken)
                        .content("{\"name\":\"Q3 quota\",\"ownerUserId\":\"" + rep[0] + "\",\"metric\":\"REVENUE\","
                                + "\"targetValue\":10000,\"periodStart\":\"2026-07-01\",\"periodEnd\":\"2026-09-30\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Q3 quota"))
                .andReturn();
        String goalId = readField(createResult, "data", "id");
        assertThat(objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data").get("actualValue").decimalValue())
                .isEqualByComparingTo("0");

        mockMvc.perform(authed(get("/api/v1/sales-goals"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        MvcResult getResult = mockMvc
                .perform(authed(get("/api/v1/sales-goals/" + goalId), ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(getResult.getResponse().getContentAsString()).get("data").get("targetValue").decimalValue())
                .isEqualByComparingTo("10000");

        MvcResult updateResult = mockMvc
                .perform(authed(put("/api/v1/sales-goals/" + goalId), ownerToken)
                        .content("{\"name\":\"Q3 quota v2\",\"ownerUserId\":\"" + rep[0] + "\",\"metric\":\"REVENUE\","
                                + "\"targetValue\":20000,\"periodStart\":\"2026-07-01\",\"periodEnd\":\"2026-09-30\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Q3 quota v2"))
                .andReturn();
        assertThat(objectMapper.readTree(updateResult.getResponse().getContentAsString()).get("data").get("targetValue").decimalValue())
                .isEqualByComparingTo("20000");

        mockMvc.perform(authed(delete("/api/v1/sales-goals/" + goalId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/sales-goals/" + goalId), ownerToken)).andExpect(status().isNotFound());
    }

    @Test
    void create_neitherTargetSet_returns400() throws Exception {
        String ownerToken = registerOwner("goals-notarget-owner");

        mockMvc.perform(authed(post("/api/v1/sales-goals"), ownerToken)
                        .content("{\"name\":\"Bad goal\",\"metric\":\"REVENUE\",\"targetValue\":1000,"
                                + "\"periodStart\":\"2026-01-01\",\"periodEnd\":\"2026-12-31\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_bothTargetsSet_returns400() throws Exception {
        String ownerToken = registerOwner("goals-bothtarget-owner");
        String[] rep = inviteAndLogin(ownerToken, "goals-bothtarget-rep");
        String teamId = createTeam(ownerToken, "Inbound");

        mockMvc.perform(authed(post("/api/v1/sales-goals"), ownerToken)
                        .content("{\"name\":\"Bad goal\",\"ownerUserId\":\"" + rep[0] + "\",\"teamId\":\"" + teamId
                                + "\",\"metric\":\"REVENUE\",\"targetValue\":1000,\"periodStart\":\"2026-01-01\",\"periodEnd\":\"2026-12-31\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_periodEndBeforePeriodStart_returns400() throws Exception {
        String ownerToken = registerOwner("goals-badperiod-owner");
        String[] rep = inviteAndLogin(ownerToken, "goals-badperiod-rep");

        mockMvc.perform(authed(post("/api/v1/sales-goals"), ownerToken)
                        .content("{\"name\":\"Bad goal\",\"ownerUserId\":\"" + rep[0] + "\",\"metric\":\"REVENUE\",\"targetValue\":1000,"
                                + "\"periodStart\":\"2026-12-31\",\"periodEnd\":\"2026-01-01\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void memberWithoutPermission_forbiddenOnAdminEndpoints_butMineStillWorks() throws Exception {
        String ownerToken = registerOwner("goals-scope-owner");
        String[] rep = inviteAndLogin(ownerToken, "goals-scope-rep");

        mockMvc.perform(authed(post("/api/v1/sales-goals"), ownerToken)
                        .content("{\"name\":\"Rep quota\",\"ownerUserId\":\"" + rep[0] + "\",\"metric\":\"REVENUE\",\"targetValue\":1000,"
                                + "\"periodStart\":\"2020-01-01\",\"periodEnd\":\"2030-12-31\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(authed(get("/api/v1/sales-goals"), rep[1])).andExpect(status().isForbidden());
        mockMvc.perform(authed(post("/api/v1/sales-goals"), rep[1])
                        .content("{\"name\":\"Bad\",\"ownerUserId\":\"" + rep[0] + "\",\"metric\":\"REVENUE\",\"targetValue\":1,"
                                + "\"periodStart\":\"2026-01-01\",\"periodEnd\":\"2026-12-31\"}"))
                .andExpect(status().isForbidden());

        // No SALES_GOAL permission needed for the caller's own goals - same self-scoped shape notification/'s inbox uses.
        mockMvc.perform(authed(get("/api/v1/sales-goals/mine"), rep[1]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Rep quota"));
    }

    @Test
    void individualGoal_realWonOpportunity_movesProgress() throws Exception {
        String ownerToken = registerOwner("goals-individual-owner");
        String[] rep = inviteAndLogin(ownerToken, "goals-individual-rep");
        String ownerId = readField(mockMvc.perform(authed(get("/api/v1/users/me"), ownerToken)).andReturn(), "data", "id");

        LocalDate today = LocalDate.now();
        mockMvc.perform(authed(post("/api/v1/sales-goals"), ownerToken)
                        .content("{\"name\":\"My quota\",\"ownerUserId\":\"" + ownerId + "\",\"metric\":\"REVENUE\",\"targetValue\":1000,"
                                + "\"periodStart\":\"" + today.minusDays(1) + "\",\"periodEnd\":\"" + today.plusDays(1) + "\"}"))
                .andExpect(status().isCreated());

        UUID accountId = createAccount(ownerToken, "Acme Quota Co");
        String opportunityId = createOpportunity(ownerToken, accountId, new BigDecimal("400.00"));
        mockMvc.perform(authed(patch("/api/v1/opportunities/" + opportunityId + "/stage"), ownerToken).content("{\"stage\":\"CLOSED_WON\"}"))
                .andExpect(status().isOk());

        // A second won deal on the OTHER teammate must not count toward this goal.
        UUID accountId2 = createAccount(rep[1], "Someone Else Co");
        String otherOpportunityId = createOpportunity(rep[1], accountId2, new BigDecimal("999.00"));
        mockMvc.perform(authed(patch("/api/v1/opportunities/" + otherOpportunityId + "/stage"), rep[1]).content("{\"stage\":\"CLOSED_WON\"}"))
                .andExpect(status().isOk());

        MvcResult goalsResult = mockMvc.perform(authed(get("/api/v1/sales-goals"), ownerToken)).andExpect(status().isOk()).andReturn();
        JsonNode goal = objectMapper.readTree(goalsResult.getResponse().getContentAsString()).get("data").get("content").get(0);
        assertThat(goal.get("actualValue").decimalValue()).isEqualByComparingTo("400.00");
        assertThat(goal.get("percentComplete").decimalValue()).isEqualByComparingTo("40.0");
    }

    @Test
    void teamGoal_sumsWonDealsAcrossCurrentTeamMembers() throws Exception {
        String ownerToken = registerOwner("goals-team-owner");
        String[] repOne = inviteAndLogin(ownerToken, "goals-team-rep1");
        String[] repTwo = inviteAndLogin(ownerToken, "goals-team-rep2");
        String teamId = createTeam(ownerToken, "Closers");
        putOnTeam(ownerToken, repOne[0], teamId);
        putOnTeam(ownerToken, repTwo[0], teamId);

        LocalDate today = LocalDate.now();
        MvcResult createResult = mockMvc
                .perform(authed(post("/api/v1/sales-goals"), ownerToken)
                        .content("{\"name\":\"Team quota\",\"teamId\":\"" + teamId + "\",\"metric\":\"DEAL_COUNT\",\"targetValue\":2,"
                                + "\"periodStart\":\"" + today.minusDays(1) + "\",\"periodEnd\":\"" + today.plusDays(1) + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String goalId = readField(createResult, "data", "id");

        UUID accountOne = createAccount(repOne[1], "Rep One Co");
        String dealOne = createOpportunity(repOne[1], accountOne, new BigDecimal("100.00"));
        mockMvc.perform(authed(patch("/api/v1/opportunities/" + dealOne + "/stage"), repOne[1]).content("{\"stage\":\"CLOSED_WON\"}"))
                .andExpect(status().isOk());

        UUID accountTwo = createAccount(repTwo[1], "Rep Two Co");
        String dealTwo = createOpportunity(repTwo[1], accountTwo, new BigDecimal("200.00"));
        mockMvc.perform(authed(patch("/api/v1/opportunities/" + dealTwo + "/stage"), repTwo[1]).content("{\"stage\":\"CLOSED_WON\"}"))
                .andExpect(status().isOk());

        MvcResult goalResult =
                mockMvc.perform(authed(get("/api/v1/sales-goals/" + goalId), ownerToken)).andExpect(status().isOk()).andReturn();
        JsonNode goal = objectMapper.readTree(goalResult.getResponse().getContentAsString()).get("data");
        assertThat(goal.get("actualValue").decimalValue()).isEqualByComparingTo("2");
        assertThat(goal.get("percentComplete").decimalValue()).isEqualByComparingTo("100.0");

        // Both teammates should also see this same team goal via /mine.
        mockMvc.perform(authed(get("/api/v1/sales-goals/mine"), repOne[1]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].teamId").value(teamId));
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
