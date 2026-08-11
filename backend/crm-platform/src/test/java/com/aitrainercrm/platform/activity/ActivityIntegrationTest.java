package com.aitrainercrm.platform.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitrainercrm.platform.account.dto.CreateAccountRequest;
import com.aitrainercrm.platform.activity.dto.CreateActivityRequest;
import com.aitrainercrm.platform.activity.entity.Activity;
import com.aitrainercrm.platform.auth.dto.LoginRequest;
import com.aitrainercrm.platform.auth.dto.RegisterRequest;
import com.aitrainercrm.platform.common.dto.AssignOwnerRequest;
import com.aitrainercrm.platform.support.AbstractIntegrationTest;
import com.aitrainercrm.platform.user.dto.CreateUserRequest;
import com.aitrainercrm.platform.user.entity.User;
import com.aitrainercrm.platform.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
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
 * End-to-end coverage for the Activity module, following the same real-database,
 * real-@PreAuthorize pattern as CrmDomainIntegrationTest: logs a task against an
 * account, completes/reopens it, reassigns it, deletes it, and - the scope-filtering
 * piece that matters most here - proves a MEMBER teammate's activity list is filtered
 * to their own activities while the OWNER (ORGANIZATION scope) sees every activity in
 * the org, same as it already does for accounts.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ActivityIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void logCompleteReassignDelete_andOwnScopeListFiltering_endToEnd() throws Exception {
        String ownerEmail = "activity-owner-%d@example.com".formatted(System.nanoTime());
        RegisterRequest registerRequest =
                new RegisterRequest(ownerEmail, "Str0ng!Passw0rd", "Owner", "Person", "Initech");
        MvcResult registerResult = mockMvc
                .perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String ownerToken = readField(registerResult, "data", "accessToken");
        String ownerId = readField(registerResult, "data", "userId");

        CreateAccountRequest createAccount = new CreateAccountRequest(
                "Initech Corp", null, null, null, null, null, null, null, null, null, null, null, null);
        MvcResult accountResult = mockMvc
                .perform(authed(post("/api/v1/accounts"), ownerToken).content(objectMapper.writeValueAsString(createAccount)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID accountId = UUID.fromString(readField(accountResult, "data", "id"));

        // --- Log a task against the account ---
        CreateActivityRequest createActivity = new CreateActivityRequest(
                Activity.Type.TASK, "Follow up on renewal", "Call about the Q3 renewal",
                Activity.Priority.HIGH, Instant.now().plusSeconds(86400), Activity.RelatedToType.ACCOUNT, accountId, null);
        MvcResult activityResult = mockMvc
                .perform(authed(post("/api/v1/activities"), ownerToken).content(objectMapper.writeValueAsString(createActivity)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.subject").value("Follow up on renewal"))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.ownerId").value(ownerId))
                .andReturn();
        String activityId = readField(activityResult, "data", "id");

        // --- Timeline filter: this account has exactly this one activity ---
        mockMvc.perform(authed(get("/api/v1/activities")
                        .param("relatedToType", "ACCOUNT")
                        .param("relatedToId", accountId.toString()), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(activityId));

        // --- Complete it: completedAt gets stamped ---
        MvcResult completeResult = mockMvc
                .perform(authed(patch("/api/v1/activities/" + activityId + "/status"), ownerToken)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andReturn();
        assertThat(readField(completeResult, "data", "completedAt")).isNotBlank();

        // --- Reopen it: completedAt clears back out ---
        MvcResult reopenResult = mockMvc
                .perform(authed(patch("/api/v1/activities/" + activityId + "/status"), ownerToken)
                        .content("{\"status\":\"OPEN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andReturn();
        assertThat(readField(reopenResult, "data", "completedAt")).isBlank();

        // --- Invite a MEMBER teammate ---
        String teammateEmail = "activity-teammate-%d@example.com".formatted(System.nanoTime());
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

        // MEMBER holds ACTIVITY:CREATE:OWN/TEAM - null ownerId defaults to themselves. No
        // ACCOUNT-level check is done on the related-to reference (see
        // ActivityService#validateRelatedTo), so logging a note against the owner's account
        // is allowed - it's the activity's own owner_id that scope filtering keys off of.
        CreateActivityRequest teammateActivity = new CreateActivityRequest(
                Activity.Type.NOTE, "Left a voicemail", null, null, null, Activity.RelatedToType.ACCOUNT, accountId, null);
        MvcResult teammateActivityResult = mockMvc
                .perform(authed(post("/api/v1/activities"), teammateToken).content(objectMapper.writeValueAsString(teammateActivity)))
                .andExpect(status().isCreated())
                .andReturn();
        String teammateActivityId = readField(teammateActivityResult, "data", "id");

        // OWNER holds ACTIVITY:READ:ORGANIZATION - sees both activities.
        mockMvc.perform(authed(get("/api/v1/activities"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        // MEMBER's highest granted scope is TEAM, which degrades to themselves (no team
        // assignment exists yet) - sees only the note they just logged.
        mockMvc.perform(authed(get("/api/v1/activities"), teammateToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(teammateActivityId));

        // --- Reassign the owner's original task to the teammate ---
        mockMvc.perform(authed(patch("/api/v1/activities/" + activityId + "/owner"), ownerToken)
                        .content(objectMapper.writeValueAsString(new AssignOwnerRequest(teammate.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ownerId").value(teammate.getId().toString()));

        // Now both activities are owned by the teammate - their OWN/TEAM-scope list sees both.
        mockMvc.perform(authed(get("/api/v1/activities"), teammateToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        // --- Delete the teammate's note; ORGANIZATION-scope OWNER may delete anyone's activity ---
        mockMvc.perform(authed(delete("/api/v1/activities/" + teammateActivityId), ownerToken))
                .andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/activities/" + teammateActivityId), ownerToken))
                .andExpect(status().isNotFound());
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
