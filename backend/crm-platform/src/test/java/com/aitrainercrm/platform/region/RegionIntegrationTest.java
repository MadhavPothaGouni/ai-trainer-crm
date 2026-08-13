package com.aitrainercrm.platform.region;

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
 * End-to-end coverage for what only real HTTP + real Postgres can pin down: full CRUD gated by
 * REGION:*:ORGANIZATION, the delete guards actually blocking on real child rows, a real cycle
 * rejection through the API (not just RegionService's own in-memory traversal), and - the module's
 * central claim - a real two-level rollup summing real won Opportunities across two different
 * teams in two different regions of the same subtree. {@code RegionServiceTest} covers the
 * cycle-detection and aggregation-math edge cases with mocks.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class RegionIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void regionCrud_endToEnd_withDeleteGuards() throws Exception {
        String ownerToken = registerOwner("regions-crud-owner");

        String parentId = createRegion(ownerToken, "North America", null);
        String childId = createRegion(ownerToken, "US-West", parentId);

        mockMvc.perform(authed(get("/api/v1/regions"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        // Can't delete a region that still has a child region.
        mockMvc.perform(authed(delete("/api/v1/regions/" + parentId), ownerToken)).andExpect(status().isConflict());

        mockMvc.perform(authed(put("/api/v1/regions/" + childId), ownerToken)
                        .content("{\"name\":\"US-West Coast\",\"parentRegionId\":\"" + parentId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("US-West Coast"));

        mockMvc.perform(authed(delete("/api/v1/regions/" + childId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(delete("/api/v1/regions/" + parentId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/regions/" + parentId), ownerToken)).andExpect(status().isNotFound());
    }

    @Test
    void update_reparentingToOwnDescendant_returns400() throws Exception {
        String ownerToken = registerOwner("regions-cycle-owner");
        String parentId = createRegion(ownerToken, "North America", null);
        String childId = createRegion(ownerToken, "US-West", parentId);

        mockMvc.perform(authed(put("/api/v1/regions/" + parentId), ownerToken)
                        .content("{\"name\":\"North America\",\"parentRegionId\":\"" + childId + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteRegionStillClaimedByATeam_returns409() throws Exception {
        String ownerToken = registerOwner("regions-team-guard-owner");
        String regionId = createRegion(ownerToken, "West", null);
        mockMvc.perform(authed(post("/api/v1/teams"), ownerToken).content("{\"name\":\"Closers\",\"regionId\":\"" + regionId + "\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(authed(delete("/api/v1/regions/" + regionId), ownerToken)).andExpect(status().isConflict());
    }

    @Test
    void assigningATeamToAnUnknownRegion_returns404() throws Exception {
        String ownerToken = registerOwner("regions-unknown-owner");

        mockMvc.perform(authed(post("/api/v1/teams"), ownerToken)
                        .content("{\"name\":\"Closers\",\"regionId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rollup_sumsRealWonOpportunities_acrossParentAndChildRegionTeams() throws Exception {
        String ownerToken = registerOwner("regions-rollup-owner");
        String[] repWest = inviteAndLogin(ownerToken, "regions-rollup-west");
        String[] repCentral = inviteAndLogin(ownerToken, "regions-rollup-central");

        String parentId = createRegion(ownerToken, "North America", null);
        String childId = createRegion(ownerToken, "US-West", parentId);

        String centralTeamId = createTeam(ownerToken, "Central Team", parentId);
        String westTeamId = createTeam(ownerToken, "West Team", childId);
        putOnTeam(ownerToken, repCentral[0], centralTeamId);
        putOnTeam(ownerToken, repWest[0], westTeamId);

        UUID accountA = createAccount(repCentral[1], "Central Co");
        String dealA = createOpportunity(repCentral[1], accountA, new BigDecimal("1000.00"));
        mockMvc.perform(authed(patch("/api/v1/opportunities/" + dealA + "/stage"), repCentral[1]).content("{\"stage\":\"CLOSED_WON\"}"))
                .andExpect(status().isOk());

        UUID accountB = createAccount(repWest[1], "West Co");
        String dealB = createOpportunity(repWest[1], accountB, new BigDecimal("2500.00"));
        mockMvc.perform(authed(patch("/api/v1/opportunities/" + dealB + "/stage"), repWest[1]).content("{\"stage\":\"CLOSED_WON\"}"))
                .andExpect(status().isOk());

        // A third deal on a rep with no team/region at all must never show up in any rollup.
        UUID accountC = createAccount(ownerToken, "Unrelated Co");
        String dealC = createOpportunity(ownerToken, accountC, new BigDecimal("999999.00"));
        mockMvc.perform(authed(patch("/api/v1/opportunities/" + dealC + "/stage"), ownerToken).content("{\"stage\":\"CLOSED_WON\"}"))
                .andExpect(status().isOk());

        MvcResult rollupResult =
                mockMvc.perform(authed(get("/api/v1/regions/" + parentId + "/rollup"), ownerToken)).andExpect(status().isOk()).andReturn();
        JsonNode rollup = objectMapper.readTree(rollupResult.getResponse().getContentAsString()).get("data");
        assertThat(rollup.get("descendantRegionCount").asInt()).isEqualTo(1);
        assertThat(rollup.get("teamCount").asInt()).isEqualTo(2);
        assertThat(rollup.get("userCount").asInt()).isEqualTo(2);
        assertThat(rollup.get("wonOpportunityCount").asLong()).isEqualTo(2);
        assertThat(rollup.get("wonValue").decimalValue()).isEqualByComparingTo("3500.00");

        // The child region's own rollup only sees its own team's deal, not the parent's.
        MvcResult childRollupResult =
                mockMvc.perform(authed(get("/api/v1/regions/" + childId + "/rollup"), ownerToken)).andExpect(status().isOk()).andReturn();
        JsonNode childRollup = objectMapper.readTree(childRollupResult.getResponse().getContentAsString()).get("data");
        assertThat(childRollup.get("wonOpportunityCount").asLong()).isEqualTo(1);
        assertThat(childRollup.get("wonValue").decimalValue()).isEqualByComparingTo("2500.00");
    }

    private String createRegion(String token, String name, String parentRegionId) throws Exception {
        String parentField = parentRegionId == null ? "" : ",\"parentRegionId\":\"" + parentRegionId + "\"";
        MvcResult result = mockMvc
                .perform(authed(post("/api/v1/regions"), token).content("{\"name\":\"" + name + "\"" + parentField + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        return readField(result, "data", "id");
    }

    private String createTeam(String ownerToken, String name, String regionId) throws Exception {
        MvcResult result = mockMvc
                .perform(authed(post("/api/v1/teams"), ownerToken).content("{\"name\":\"" + name + "\",\"regionId\":\"" + regionId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return readField(result, "data", "id");
    }

    private void putOnTeam(String ownerToken, String userId, String teamId) throws Exception {
        mockMvc.perform(authed(patch("/api/v1/users/" + userId + "/team"), ownerToken).content("{\"teamId\":\"" + teamId + "\"}"))
                .andExpect(status().isOk());
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
