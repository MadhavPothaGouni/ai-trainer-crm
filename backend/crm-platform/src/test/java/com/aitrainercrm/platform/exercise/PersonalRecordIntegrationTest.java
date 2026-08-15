package com.aitrainercrm.platform.exercise;

import static org.assertj.core.api.Assertions.assertThat;
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
 * End-to-end coverage for personal records - see V64's migration comment and
 * {@code PersonalRecord}'s javadoc. Covers the one piece of real business logic:
 * {@code PersonalRecordService#assertIsImprovement} rejecting a new record that doesn't beat the
 * contact's current best for that exercise+record-type combination, mirrored here the same way
 * {@code RoomBookingIntegrationTest} covers its overlap rule, plus the same MEMBER-teammate
 * owner-scope split every other core-CRM occurrence resource covers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class PersonalRecordIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void personalRecordLifecycle_endToEnd() throws Exception {
        String ownerToken = registerOwner("personal-record-crud");
        String contactId = createContact(ownerToken, "Jamie", "Client");

        MvcResult exerciseResult = mockMvc
                .perform(authed(post("/api/v1/exercises"), ownerToken)
                        .content(
                                """
                                {"name":"Barbell Back Squat","category":"STRENGTH","primaryMuscleGroup":"LEGS",
                                "equipment":"BARBELL","difficultyLevel":"INTERMEDIATE"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String exerciseId = readField(exerciseResult, "data", "id");

        // Against a nonexistent exercise is rejected.
        mockMvc.perform(authed(post("/api/v1/personal-records"), ownerToken)
                        .content(
                                """
                                {"contactId":"%s","exerciseId":"00000000-0000-0000-0000-000000000000",
                                "recordType":"ONE_REP_MAX","value":200}
                                """
                                        .formatted(contactId)))
                .andExpect(status().isNotFound());

        // First record for this exercise+type always succeeds - nothing to beat yet.
        MvcResult firstRecord = mockMvc
                .perform(authed(post("/api/v1/personal-records"), ownerToken)
                        .content(
                                """
                                {"contactId":"%s","exerciseId":"%s","recordType":"ONE_REP_MAX","value":200}
                                """
                                        .formatted(contactId, exerciseId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.value").value(200))
                .andReturn();
        String firstRecordId = readField(firstRecord, "data", "id");
        assertThat(firstRecordId).isNotBlank();

        // A value that doesn't beat the current best is rejected.
        mockMvc.perform(authed(post("/api/v1/personal-records"), ownerToken)
                        .content(
                                """
                                {"contactId":"%s","exerciseId":"%s","recordType":"ONE_REP_MAX","value":195}
                                """
                                        .formatted(contactId, exerciseId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PERSONAL_RECORD_NOT_AN_IMPROVEMENT"));

        // A value that beats it succeeds.
        MvcResult secondRecord = mockMvc
                .perform(authed(post("/api/v1/personal-records"), ownerToken)
                        .content(
                                """
                                {"contactId":"%s","exerciseId":"%s","recordType":"ONE_REP_MAX","value":225}
                                """
                                        .formatted(contactId, exerciseId)))
                .andExpect(status().isCreated())
                .andReturn();
        String secondRecordId = readField(secondRecord, "data", "id");

        // A different record type for the same exercise is independent - 50 reps is a fresh record for MAX_REPS.
        mockMvc.perform(authed(post("/api/v1/personal-records"), ownerToken)
                        .content(
                                """
                                {"contactId":"%s","exerciseId":"%s","recordType":"MAX_REPS","value":15}
                                """
                                        .formatted(contactId, exerciseId)))
                .andExpect(status().isCreated());

        // Editing the older (now-beaten) record to a value that still doesn't beat the newer 225 is rejected too.
        mockMvc.perform(authed(put("/api/v1/personal-records/" + firstRecordId), ownerToken).content("{\"value\":210}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PERSONAL_RECORD_NOT_AN_IMPROVEMENT"));

        // But editing it past 225 succeeds.
        mockMvc.perform(authed(put("/api/v1/personal-records/" + firstRecordId), ownerToken).content("{\"value\":230,\"notes\":\"New PR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.value").value(230))
                .andExpect(jsonPath("$.data.notes").value("New PR"));

        mockMvc.perform(authed(get("/api/v1/personal-records"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3));

        mockMvc.perform(authed(delete("/api/v1/personal-records/" + secondRecordId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/personal-records/" + secondRecordId), ownerToken)).andExpect(status().isNotFound());

        // --- A MEMBER teammate: PERSONAL_RECORD is a core CRM resource, so they hold CREATE:OWN by default. ---
        String teammateEmail = "personal-record-teammate-%d@example.com".formatted(System.nanoTime());
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

        mockMvc.perform(authed(post("/api/v1/personal-records"), teammateToken)
                        .content(
                                """
                                {"contactId":"%s","exerciseId":"%s","recordType":"MAX_WEIGHT","value":100}
                                """
                                        .formatted(contactId, exerciseId)))
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
