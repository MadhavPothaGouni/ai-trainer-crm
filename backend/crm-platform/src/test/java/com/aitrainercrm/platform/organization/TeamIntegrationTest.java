package com.aitrainercrm.platform.organization;

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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * End-to-end coverage for the Team module - the management API that finally makes TEAM/DEPARTMENT
 * scope reachable (see ScopeAuthorizationService's javadoc and V16's migration comment). Covers
 * full Team CRUD, the soft-delete-doesn't-orphan-members behavior, lead-user validation, and the
 * user-side team assignment endpoint this module ships alongside (PATCH /users/{id}/team).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class TeamIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void createUpdateListAndDeleteTeam_endToEnd() throws Exception {
        String ownerToken = registerOwner("team-crud");

        MvcResult createResult = mockMvc
                .perform(authed(post("/api/v1/teams"), ownerToken).content("{\"name\":\"Rocket Sales\",\"department\":\"Sales\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Rocket Sales"))
                .andExpect(jsonPath("$.data.department").value("Sales"))
                .andExpect(jsonPath("$.data.leadUserId").doesNotExist())
                .andReturn();
        String teamId = readField(createResult, "data", "id");
        assertThat(teamId).isNotBlank();

        mockMvc.perform(authed(put("/api/v1/teams/" + teamId), ownerToken).content("{\"name\":\"Rocket Sales EMEA\",\"department\":\"Sales\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Rocket Sales EMEA"));

        mockMvc.perform(authed(get("/api/v1/teams"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(authed(delete("/api/v1/teams/" + teamId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/teams/" + teamId), ownerToken)).andExpect(status().isNotFound());
        mockMvc.perform(authed(get("/api/v1/teams"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void createTeam_withLeadUserFromAnotherOrganization_returns404() throws Exception {
        String ownerToken = registerOwner("team-badlead");
        String otherOrgOwnerToken = registerOwner("team-otherorg");
        String otherOrgUserId = readField(
                mockMvc.perform(authed(get("/api/v1/users/me"), otherOrgOwnerToken)).andExpect(status().isOk()).andReturn(),
                "data", "id");

        mockMvc.perform(authed(post("/api/v1/teams"), ownerToken).content("{\"name\":\"Support\",\"leadUserId\":\"" + otherOrgUserId + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createTeam_withRealLeadUser_succeeds() throws Exception {
        String ownerToken = registerOwner("team-goodlead");
        String ownerId = readField(mockMvc.perform(authed(get("/api/v1/users/me"), ownerToken)).andExpect(status().isOk()).andReturn(), "data", "id");

        mockMvc.perform(authed(post("/api/v1/teams"), ownerToken).content("{\"name\":\"Leadership\",\"leadUserId\":\"" + ownerId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.leadUserId").value(ownerId));
    }

    @Test
    void assignAndUnassignUserTeam_reflectsOnTheUserRecord() throws Exception {
        String ownerToken = registerOwner("team-assign");
        String ownerId = readField(mockMvc.perform(authed(get("/api/v1/users/me"), ownerToken)).andExpect(status().isOk()).andReturn(), "data", "id");

        MvcResult teamResult = mockMvc
                .perform(authed(post("/api/v1/teams"), ownerToken).content("{\"name\":\"Rocket Sales\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String teamId = readField(teamResult, "data", "id");

        mockMvc.perform(authed(patch("/api/v1/users/" + ownerId + "/team"), ownerToken).content("{\"teamId\":\"" + teamId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teamId").value(teamId));

        mockMvc.perform(authed(get("/api/v1/users/" + ownerId), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teamId").value(teamId));

        // teamId:null unassigns rather than being rejected as missing input - see UpdateUserTeamRequest's javadoc.
        mockMvc.perform(authed(patch("/api/v1/users/" + ownerId + "/team"), ownerToken).content("{\"teamId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teamId").doesNotExist());
    }

    @Test
    void assignUserToUnknownTeam_returns404() throws Exception {
        String ownerToken = registerOwner("team-assign-unknown");
        String ownerId = readField(mockMvc.perform(authed(get("/api/v1/users/me"), ownerToken)).andExpect(status().isOk()).andReturn(), "data", "id");

        mockMvc.perform(authed(patch("/api/v1/users/" + ownerId + "/team"), ownerToken).content("{\"teamId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingATeam_doesNotOrphanAMemberStillAssignedToIt() throws Exception {
        String ownerToken = registerOwner("team-delete-with-member");
        String ownerId = readField(mockMvc.perform(authed(get("/api/v1/users/me"), ownerToken)).andExpect(status().isOk()).andReturn(), "data", "id");

        String teamId = readField(
                mockMvc.perform(authed(post("/api/v1/teams"), ownerToken).content("{\"name\":\"Rocket Sales\"}"))
                        .andExpect(status().isCreated())
                        .andReturn(),
                "data", "id");
        mockMvc.perform(authed(patch("/api/v1/users/" + ownerId + "/team"), ownerToken).content("{\"teamId\":\"" + teamId + "\"}"))
                .andExpect(status().isOk());

        // Soft delete succeeds even though the owner is still on this team - see TeamService's
        // javadoc for why that's fine (no FK violation, no silent reassignment).
        mockMvc.perform(authed(delete("/api/v1/teams/" + teamId), ownerToken)).andExpect(status().isOk());

        mockMvc.perform(authed(get("/api/v1/users/" + ownerId), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teamId").value(teamId));
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
