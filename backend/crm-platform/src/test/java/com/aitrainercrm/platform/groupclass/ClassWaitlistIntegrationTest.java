package com.aitrainercrm.platform.groupclass;

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
 * End-to-end coverage for class waitlists - see V61's migration comment and {@code ClassWaitlist}'s
 * javadoc. Covers the two behaviors unique to this resource: server-computed {@code position}
 * incrementing across entries for the same session, and {@code notifiedAt}'s stamp-once semantics,
 * plus the same MEMBER-teammate owner-scope split {@code NoShowRecordIntegrationTest} covers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ClassWaitlistIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void classWaitlistLifecycle_endToEnd() throws Exception {
        String ownerToken = registerOwner("waitlist-crud");
        String jamieId = createContact(ownerToken, "Jamie", "Client");
        String alexId = createContact(ownerToken, "Alex", "Client");

        MvcResult classResult = mockMvc
                .perform(authed(post("/api/v1/group-classes"), ownerToken)
                        .content("{\"name\":\"Spin 45\",\"durationMinutes\":45,\"capacity\":1}"))
                .andExpect(status().isCreated())
                .andReturn();
        String groupClassId = readField(classResult, "data", "id");

        MvcResult sessionResult = mockMvc
                .perform(authed(post("/api/v1/class-sessions"), ownerToken)
                        .content(
                                """
                                {"groupClassId":"%s","startsAt":"2026-03-01T14:00:00Z","endsAt":"2026-03-01T14:45:00Z"}
                                """
                                        .formatted(groupClassId)))
                .andExpect(status().isCreated())
                .andReturn();
        String sessionId = readField(sessionResult, "data", "id");

        // Waitlisting against a nonexistent session is rejected.
        mockMvc.perform(authed(post("/api/v1/class-waitlists"), ownerToken)
                        .content("{\"classSessionId\":\"00000000-0000-0000-0000-000000000000\",\"contactId\":\"%s\"}".formatted(jamieId)))
                .andExpect(status().isNotFound());

        // First entry for the session gets position 1.
        MvcResult jamieEntry = mockMvc
                .perform(authed(post("/api/v1/class-waitlists"), ownerToken)
                        .content("{\"classSessionId\":\"%s\",\"contactId\":\"%s\"}".formatted(sessionId, jamieId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.position").value(1))
                .andExpect(jsonPath("$.data.status").value("WAITING"))
                .andReturn();
        String jamieEntryId = readField(jamieEntry, "data", "id");

        // Second entry for the same session gets position 2.
        MvcResult alexEntry = mockMvc
                .perform(authed(post("/api/v1/class-waitlists"), ownerToken)
                        .content("{\"classSessionId\":\"%s\",\"contactId\":\"%s\"}".formatted(sessionId, alexId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.position").value(2))
                .andReturn();
        String alexEntryId = readField(alexEntry, "data", "id");
        assertThat(alexEntryId).isNotEqualTo(jamieEntryId);

        // Moving to NOTIFIED stamps notifiedAt; a later re-notify doesn't lose the original stamp.
        MvcResult notifiedResult = mockMvc
                .perform(authed(patch("/api/v1/class-waitlists/" + jamieEntryId + "/status"), ownerToken).content("{\"status\":\"NOTIFIED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NOTIFIED"))
                .andExpect(jsonPath("$.data.notifiedAt").exists())
                .andReturn();
        String firstNotifiedAt = readField(notifiedResult, "data", "notifiedAt");

        MvcResult convertedResult = mockMvc
                .perform(authed(patch("/api/v1/class-waitlists/" + jamieEntryId + "/status"), ownerToken).content("{\"status\":\"CONVERTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONVERTED"))
                .andReturn();
        assertThat(readField(convertedResult, "data", "notifiedAt")).isEqualTo(firstNotifiedAt);

        // Free state machine: moving CONVERTED back to WAITING is a legitimate correction.
        mockMvc.perform(authed(patch("/api/v1/class-waitlists/" + jamieEntryId + "/status"), ownerToken).content("{\"status\":\"WAITING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAITING"));

        // Editing notes.
        mockMvc.perform(authed(put("/api/v1/class-waitlists/" + jamieEntryId), ownerToken).content("{\"notes\":\"Prefers morning slots\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notes").value("Prefers morning slots"));

        mockMvc.perform(authed(get("/api/v1/class-waitlists"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        mockMvc.perform(authed(delete("/api/v1/class-waitlists/" + alexEntryId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/class-waitlists/" + alexEntryId), ownerToken)).andExpect(status().isNotFound());

        // A third waitlist entry, now that Alex's slot was removed, gets position 2 again (WAITING count is back to 1).
        MvcResult samId = createContactResult(ownerToken, "Sam", "Client");
        mockMvc.perform(authed(post("/api/v1/class-waitlists"), ownerToken)
                        .content("{\"classSessionId\":\"%s\",\"contactId\":\"%s\"}".formatted(sessionId, readField(samId, "data", "id"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.position").value(2));

        // --- A MEMBER teammate: CLASS_WAITLIST is a core CRM resource, so they hold CREATE:OWN by default. ---
        String teammateEmail = "waitlist-teammate-%d@example.com".formatted(System.nanoTime());
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

        mockMvc.perform(authed(post("/api/v1/class-waitlists"), teammateToken)
                        .content("{\"classSessionId\":\"%s\",\"contactId\":\"%s\"}".formatted(sessionId, jamieId)))
                .andExpect(status().isCreated());

        // But the teammate can't see the owner's original (now-WAITING) entry - OWN scope only.
        mockMvc.perform(authed(get("/api/v1/class-waitlists/" + jamieEntryId), teammateToken)).andExpect(status().isForbidden());
    }

    private String createContact(String token, String firstName, String lastName) throws Exception {
        return readField(createContactResult(token, firstName, lastName), "data", "id");
    }

    private MvcResult createContactResult(String token, String firstName, String lastName) throws Exception {
        return mockMvc
                .perform(authed(post("/api/v1/contacts"), token)
                        .content("{\"firstName\":\"%s\",\"lastName\":\"%s\"}".formatted(firstName, lastName)))
                .andExpect(status().isCreated())
                .andReturn();
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
