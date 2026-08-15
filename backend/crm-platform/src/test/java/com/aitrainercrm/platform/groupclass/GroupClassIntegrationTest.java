package com.aitrainercrm.platform.groupclass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * End-to-end coverage for the catalog/occurrence/roster shape - see V43's migration comment.
 * Mirrors {@code MembershipIntegrationTest}'s shape (shared-catalog GroupClass behaves like
 * MembershipPlan/Product; owner-scoped ClassSession/ClassAttendance behave like Membership/
 * ClientGoal), plus the one piece of business logic neither of those cover: capacity enforcement
 * across the session/roster relationship, and checkedInAt's stamp-once behavior.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class GroupClassIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void catalogOccurrenceRosterLifecycle_endToEnd() throws Exception {
        String ownerToken = registerOwner("group-class-crud");
        String jamieId = createContact(ownerToken, "Jamie", "Client");
        String alexId = createContact(ownerToken, "Alex", "Client");
        String samId = createContact(ownerToken, "Sam", "Client");

        // --- GroupClass (catalog), capacity of 2 ---
        MvcResult classResult = mockMvc
                .perform(authed(post("/api/v1/group-classes"), ownerToken)
                        .content(
                                """
                                {"name":"Spin 45","description":"High-intensity indoor cycling","durationMinutes":45,
                                "capacity":2,"location":"Main Studio"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.active").value(true))
                .andReturn();
        String groupClassId = readField(classResult, "data", "id");
        assertThat(groupClassId).isNotBlank();

        // --- ClassSession (occurrence), ownerId defaults to the caller ---
        MvcResult sessionResult = mockMvc
                .perform(authed(post("/api/v1/class-sessions"), ownerToken)
                        .content(
                                """
                                {"groupClassId":"%s","startsAt":"2026-02-01T14:00:00Z","endsAt":"2026-02-01T14:45:00Z"}
                                """
                                        .formatted(groupClassId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andReturn();
        String sessionId = readField(sessionResult, "data", "id");
        assertThat(sessionId).isNotBlank();

        // --- Roster fills to capacity (2) ---
        MvcResult jamieAttendance = mockMvc
                .perform(authed(post("/api/v1/class-attendances"), ownerToken)
                        .content("{\"classSessionId\":\"%s\",\"contactId\":\"%s\"}".formatted(sessionId, jamieId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("REGISTERED"))
                .andReturn();
        String jamieAttendanceId = readField(jamieAttendance, "data", "id");

        MvcResult alexAttendance = mockMvc
                .perform(authed(post("/api/v1/class-attendances"), ownerToken)
                        .content("{\"classSessionId\":\"%s\",\"contactId\":\"%s\"}".formatted(sessionId, alexId)))
                .andExpect(status().isCreated())
                .andReturn();
        String alexAttendanceId = readField(alexAttendance, "data", "id");

        // --- A third registration is rejected: the session is full ---
        mockMvc.perform(authed(post("/api/v1/class-attendances"), ownerToken)
                        .content("{\"classSessionId\":\"%s\",\"contactId\":\"%s\"}".formatted(sessionId, samId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CLASS_SESSION_FULL"));

        // --- Checking in stamps checkedInAt once; a later NO_SHOW/back-to-ATTENDED correction doesn't lose it ---
        MvcResult checkInResult = mockMvc
                .perform(authed(patch("/api/v1/class-attendances/" + jamieAttendanceId + "/status"), ownerToken).content("{\"status\":\"ATTENDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ATTENDED"))
                .andExpect(jsonPath("$.data.checkedInAt").exists())
                .andReturn();
        String firstCheckedInAt = readField(checkInResult, "data", "checkedInAt");

        mockMvc.perform(authed(patch("/api/v1/class-attendances/" + jamieAttendanceId + "/status"), ownerToken).content("{\"status\":\"NO_SHOW\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NO_SHOW"));

        MvcResult recheckedResult = mockMvc
                .perform(authed(patch("/api/v1/class-attendances/" + jamieAttendanceId + "/status"), ownerToken).content("{\"status\":\"ATTENDED\"}"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(readField(recheckedResult, "data", "checkedInAt")).isEqualTo(firstCheckedInAt);

        // --- Cancelling a registration frees the seat back up ---
        mockMvc.perform(authed(patch("/api/v1/class-attendances/" + alexAttendanceId + "/status"), ownerToken).content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        mockMvc.perform(authed(post("/api/v1/class-attendances"), ownerToken)
                        .content("{\"classSessionId\":\"%s\",\"contactId\":\"%s\"}".formatted(sessionId, samId)))
                .andExpect(status().isCreated());

        // --- Session status is a free state machine, same as tickets/memberships ---
        mockMvc.perform(authed(patch("/api/v1/class-sessions/" + sessionId + "/status"), ownerToken).content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        mockMvc.perform(authed(patch("/api/v1/class-sessions/" + sessionId + "/status"), ownerToken).content("{\"status\":\"SCHEDULED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"));

        mockMvc.perform(authed(get("/api/v1/class-sessions"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(authed(delete("/api/v1/class-sessions/" + sessionId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/class-sessions/" + sessionId), ownerToken)).andExpect(status().isNotFound());

        // --- A MEMBER teammate: GROUP_CLASS isn't a core CRM resource (see RoleService#isCoreCrmResource),
        // so the default MEMBER role holds no GROUP_CLASS:CREATE authority - the class-type catalog is
        // admin-managed, same design intent as PRODUCT/MEMBERSHIP_PLAN. CLASS_SESSION and CLASS_ATTENDANCE
        // ARE core CRM resources, so MEMBER holds CREATE:OWN on both. ---
        String teammateEmail = "group-class-teammate-%d@example.com".formatted(System.nanoTime());
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

        mockMvc.perform(authed(post("/api/v1/group-classes"), teammateToken)
                        .content("{\"name\":\"Unauthorized Class\",\"durationMinutes\":30}"))
                .andExpect(status().isForbidden());

        MvcResult teammateSessionResult = mockMvc
                .perform(authed(post("/api/v1/class-sessions"), teammateToken)
                        .content(
                                """
                                {"groupClassId":"%s","startsAt":"2026-02-08T14:00:00Z","endsAt":"2026-02-08T14:45:00Z"}
                                """
                                        .formatted(groupClassId)))
                .andExpect(status().isCreated())
                .andReturn();
        String teammateSessionId = readField(teammateSessionResult, "data", "id");

        mockMvc.perform(authed(post("/api/v1/class-attendances"), teammateToken)
                        .content("{\"classSessionId\":\"%s\",\"contactId\":\"%s\"}".formatted(teammateSessionId, jamieId)))
                .andExpect(status().isCreated());
    }

    private String createContact(String token, String firstName, String lastName) throws Exception {
        MvcResult result = mockMvc
                .perform(authed(post("/api/v1/contacts"), token)
                        .content("{\"firstName\":\"%s\",\"lastName\":\"%s\"}".formatted(firstName, lastName)))
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
