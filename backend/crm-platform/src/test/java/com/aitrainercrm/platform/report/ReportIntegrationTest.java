package com.aitrainercrm.platform.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitrainercrm.platform.account.dto.CreateAccountRequest;
import com.aitrainercrm.platform.auth.dto.LoginRequest;
import com.aitrainercrm.platform.auth.dto.RegisterRequest;
import com.aitrainercrm.platform.lead.dto.CreateLeadRequest;
import com.aitrainercrm.platform.lead.entity.Lead;
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
 * End-to-end coverage for the reporting/analytics endpoints. Builds a
 * small, deliberately uneven pipeline (one open, one won, one lost
 * opportunity owned by the OWNER; one more open opportunity owned by a
 * MEMBER teammate) and a handful of leads at different statuses, then
 * checks that: pipeline-by-stage and lead-funnel are zero-filled across
 * every enum value (not just the ones with data), the leaderboard buckets
 * won/open/lost correctly per owner and sorts by wonAmount, and - the one
 * real authorization question this module raises - that REPORT isn't a
 * core CRM resource, so a default MEMBER role (unlike OWNER/ADMIN) can't
 * reach any of these three endpoints at all.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ReportIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void pipelineLeadFunnelAndLeaderboard_aggregateCorrectly_andRequireReportPermission() throws Exception {
        String ownerEmail = "report-owner-%d@example.com".formatted(System.nanoTime());
        MvcResult registerResult = mockMvc
                .perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(ownerEmail, "Str0ng!Passw0rd", "Owner", "Person", "Acme Analytics"))))
                .andExpect(status().isCreated())
                .andReturn();
        String ownerToken = readField(registerResult, "data", "accessToken");

        // --- A teammate (default MEMBER role) to own one opportunity and prove per-owner bucketing ---
        String teammateEmail = "report-teammate-%d@example.com".formatted(System.nanoTime());
        mockMvc.perform(authed(post("/api/v1/users"), ownerToken)
                        .content(objectMapper.writeValueAsString(new CreateUserRequest(teammateEmail, "New", "Teammate", null))))
                .andExpect(status().isCreated());
        String teammatePassword = "Str0ng!Passw0rd2";
        User teammate = userRepository.findByEmailAndDeletedAtIsNull(teammateEmail.trim().toLowerCase()).orElseThrow();
        teammate.setPasswordHash(passwordEncoder.encode(teammatePassword));
        userRepository.save(teammate);
        MvcResult teammateLoginResult = mockMvc
                .perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(teammateEmail, teammatePassword))))
                .andExpect(status().isOk())
                .andReturn();
        String teammateToken = readField(teammateLoginResult, "data", "accessToken");

        // --- Account + three opportunities owned by OWNER: one open, one won, one lost ---
        MvcResult accountResult = mockMvc
                .perform(authed(post("/api/v1/accounts"), ownerToken)
                        .content(objectMapper.writeValueAsString(
                                new CreateAccountRequest("Acme Corp", null, null, null, null, null, null, null, null, null, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID accountId = UUID.fromString(readField(accountResult, "data", "id"));

        UUID openOppId = createOpportunity(ownerToken, accountId, "Open deal", new BigDecimal("1000"), null);
        UUID wonOppId = createOpportunity(ownerToken, accountId, "Won deal", new BigDecimal("2000"), null);
        UUID lostOppId = createOpportunity(ownerToken, accountId, "Lost deal", new BigDecimal("500"), null);
        mockMvc.perform(authed(patch("/api/v1/opportunities/" + wonOppId + "/stage"), ownerToken).content("{\"stage\":\"CLOSED_WON\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(authed(patch("/api/v1/opportunities/" + lostOppId + "/stage"), ownerToken).content("{\"stage\":\"CLOSED_LOST\"}"))
                .andExpect(status().isOk());

        // --- One more open opportunity, owned by the teammate (MEMBER holds OPPORTUNITY:CREATE:OWN - it's a core CRM resource) ---
        UUID teammateOppId = createOpportunity(teammateToken, accountId, "Teammate's deal", new BigDecimal("300"), null);
        mockMvc.perform(authed(patch("/api/v1/opportunities/" + teammateOppId + "/stage"), teammateToken).content("{\"stage\":\"NEGOTIATION\"}"))
                .andExpect(status().isOk());

        // --- Leads at a few different statuses ---
        createLead(ownerToken, "Lead", "New");
        createLead(ownerToken, "Lead", "AlsoNew");
        UUID contactedLeadId = createLead(ownerToken, "Lead", "Contacted");
        mockMvc.perform(authed(patch("/api/v1/leads/" + contactedLeadId + "/status"), ownerToken).content("{\"status\":\"CONTACTED\"}"))
                .andExpect(status().isOk());

        // --- Pipeline by stage, as OWNER (REPORT:READ:ORGANIZATION - sees every owner) ---
        // openOppId still PROSPECTING (1000) + teammateOppId now NEGOTIATION (300); won 2000; lost 500; every other stage zero.
        MvcResult pipelineResult = mockMvc
                .perform(authed(get("/api/v1/reports/pipeline-by-stage"), ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode pipelineData = objectMapper.readTree(pipelineResult.getResponse().getContentAsString()).get("data");
        assertThat(pipelineData).hasSize(6);
        assertPipelineStage(pipelineData, "PROSPECTING", 1, "1000");
        assertPipelineStage(pipelineData, "NEGOTIATION", 1, "300");
        assertPipelineStage(pipelineData, "CLOSED_WON", 1, "2000");
        assertPipelineStage(pipelineData, "CLOSED_LOST", 1, "500");
        assertPipelineStage(pipelineData, "QUALIFICATION", 0, "0");
        assertPipelineStage(pipelineData, "PROPOSAL", 0, "0");

        // --- Lead funnel, as OWNER: NEW=2, CONTACTED=1, everything else zero-filled ---
        MvcResult funnelResult = mockMvc
                .perform(authed(get("/api/v1/reports/lead-funnel"), ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode funnelData = objectMapper.readTree(funnelResult.getResponse().getContentAsString()).get("data");
        assertThat(funnelData).hasSize(Lead.Status.values().length);
        assertFunnelStatus(funnelData, "NEW", 2);
        assertFunnelStatus(funnelData, "CONTACTED", 1);
        assertFunnelStatus(funnelData, "QUALIFIED", 0);
        assertFunnelStatus(funnelData, "CONVERTED", 0);

        // --- Leaderboard, as OWNER: owner has won 2000/1, open 1000/1, lost 1; teammate has open 300/1, won 0.
        // Sorted by wonAmount desc, so the owner (with a closed-won deal) ranks first.
        MvcResult leaderboardResult = mockMvc
                .perform(authed(get("/api/v1/reports/leaderboard"), ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode leaderboardData = objectMapper.readTree(leaderboardResult.getResponse().getContentAsString()).get("data");
        assertThat(leaderboardData).hasSize(2);
        JsonNode topRep = leaderboardData.get(0);
        assertThat(topRep.get("wonAmount").decimalValue()).isEqualByComparingTo("2000");
        assertThat(topRep.get("wonCount").asLong()).isEqualTo(1);
        assertThat(topRep.get("openAmount").decimalValue()).isEqualByComparingTo("1000");
        assertThat(topRep.get("lostCount").asLong()).isEqualTo(1);
        JsonNode secondRep = leaderboardData.get(1);
        assertThat(secondRep.get("wonCount").asLong()).isEqualTo(0);
        assertThat(secondRep.get("openAmount").decimalValue()).isEqualByComparingTo("300");

        // --- REPORT isn't a core CRM resource (see RoleService#isCoreCrmResource) - the default
        // MEMBER role holds none of it, so the teammate is forbidden from all three endpoints,
        // even though they own real pipeline data that would show up in them. ---
        mockMvc.perform(authed(get("/api/v1/reports/pipeline-by-stage"), teammateToken)).andExpect(status().isForbidden());
        mockMvc.perform(authed(get("/api/v1/reports/lead-funnel"), teammateToken)).andExpect(status().isForbidden());
        mockMvc.perform(authed(get("/api/v1/reports/leaderboard"), teammateToken)).andExpect(status().isForbidden());
    }

    private UUID createOpportunity(String token, UUID accountId, String name, BigDecimal amount, UUID ownerId) throws Exception {
        CreateOpportunityRequest request = new CreateOpportunityRequest(accountId, null, name, amount, null, null, null, ownerId);
        MvcResult result = mockMvc
                .perform(authed(post("/api/v1/opportunities"), token).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readField(result, "data", "id"));
    }

    private UUID createLead(String token, String firstName, String lastName) throws Exception {
        CreateLeadRequest request = new CreateLeadRequest(firstName, lastName, null, null, null, null, Lead.Source.OTHER, null, null);
        MvcResult result = mockMvc
                .perform(authed(post("/api/v1/leads"), token).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readField(result, "data", "id"));
    }

    private void assertPipelineStage(JsonNode pipelineData, String stage, long expectedCount, String expectedAmount) {
        JsonNode row = findByField(pipelineData, "stage", stage);
        assertThat(row.get("opportunityCount").asLong()).as("count for %s", stage).isEqualTo(expectedCount);
        assertThat(row.get("totalAmount").decimalValue()).as("amount for %s", stage).isEqualByComparingTo(expectedAmount);
    }

    private void assertFunnelStatus(JsonNode funnelData, String status, long expectedCount) {
        JsonNode row = findByField(funnelData, "status", status);
        assertThat(row.get("leadCount").asLong()).as("count for %s", status).isEqualTo(expectedCount);
    }

    private JsonNode findByField(JsonNode array, String fieldName, String fieldValue) {
        for (JsonNode node : array) {
            if (fieldValue.equals(node.get(fieldName).asText())) return node;
        }
        throw new AssertionError("No element with %s=%s in %s".formatted(fieldName, fieldValue, array));
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
