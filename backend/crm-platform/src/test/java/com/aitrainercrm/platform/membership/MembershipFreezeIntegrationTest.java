package com.aitrainercrm.platform.membership;

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
 * End-to-end coverage for membership freezes - see V62's migration comment and
 * {@code MembershipFreeze}'s javadoc. Covers the two business rules unique to this resource:
 * {@code assertValidRange} and the overlap-conflict rule {@code assertNoOverlap} adapts from
 * {@code RoomBookingIntegrationTest}'s coverage of the Instant-range version, plus the same
 * MEMBER-teammate owner-scope split every other core-CRM occurrence resource covers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class MembershipFreezeIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void membershipFreezeLifecycle_endToEnd() throws Exception {
        String ownerToken = registerOwner("freeze-crud");
        String contactId = createContact(ownerToken, "Jamie", "Client");

        MvcResult planResult = mockMvc
                .perform(authed(post("/api/v1/membership-plans"), ownerToken)
                        .content(
                                """
                                {"name":"Unlimited Monthly","billingCycle":"MONTHLY","price":149.00,"currency":"USD"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String planId = readField(planResult, "data", "id");

        MvcResult membershipResult = mockMvc
                .perform(authed(post("/api/v1/memberships"), ownerToken)
                        .content(
                                """
                                {"contactId":"%s","membershipPlanId":"%s","startDate":"2026-01-01","autoRenew":true}
                                """
                                        .formatted(contactId, planId)))
                .andExpect(status().isCreated())
                .andReturn();
        String membershipId = readField(membershipResult, "data", "id");

        // Freezing against a nonexistent membership is rejected.
        mockMvc.perform(authed(post("/api/v1/membership-freezes"), ownerToken)
                        .content(
                                """
                                {"membershipId":"00000000-0000-0000-0000-000000000000","freezeStart":"2026-03-01","freezeEnd":"2026-03-15"}
                                """))
                .andExpect(status().isNotFound());

        // An end date not after the start date is rejected.
        mockMvc.perform(authed(post("/api/v1/membership-freezes"), ownerToken)
                        .content(
                                """
                                {"membershipId":"%s","freezeStart":"2026-03-15","freezeEnd":"2026-03-01"}
                                """
                                        .formatted(membershipId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MEMBERSHIP_FREEZE_INVALID_RANGE"));

        // First freeze succeeds.
        MvcResult freezeResult = mockMvc
                .perform(authed(post("/api/v1/membership-freezes"), ownerToken)
                        .content(
                                """
                                {"membershipId":"%s","freezeStart":"2026-03-01","freezeEnd":"2026-03-15","reason":"Travel"}
                                """
                                        .formatted(membershipId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("REQUESTED"))
                .andReturn();
        String freezeId = readField(freezeResult, "data", "id");
        assertThat(freezeId).isNotBlank();

        // A second, overlapping freeze for the same membership is rejected.
        mockMvc.perform(authed(post("/api/v1/membership-freezes"), ownerToken)
                        .content(
                                """
                                {"membershipId":"%s","freezeStart":"2026-03-10","freezeEnd":"2026-03-20"}
                                """
                                        .formatted(membershipId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MEMBERSHIP_FREEZE_CONFLICT"));

        // A non-overlapping freeze for the same membership succeeds.
        mockMvc.perform(authed(post("/api/v1/membership-freezes"), ownerToken)
                        .content(
                                """
                                {"membershipId":"%s","freezeStart":"2026-04-01","freezeEnd":"2026-04-10"}
                                """
                                        .formatted(membershipId)))
                .andExpect(status().isCreated());

        // Free state machine: REQUESTED -> ACTIVE -> ENDED, with re-activation checked again.
        mockMvc.perform(authed(patch("/api/v1/membership-freezes/" + freezeId + "/status"), ownerToken).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(authed(patch("/api/v1/membership-freezes/" + freezeId + "/status"), ownerToken).content("{\"status\":\"ENDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ENDED"));

        mockMvc.perform(authed(patch("/api/v1/membership-freezes/" + freezeId + "/status"), ownerToken).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        // Editing the date range re-checks the overlap rule too.
        mockMvc.perform(authed(put("/api/v1/membership-freezes/" + freezeId), ownerToken)
                        .content(
                                """
                                {"freezeStart":"2026-04-05","freezeEnd":"2026-04-08","reason":"Travel, adjusted"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MEMBERSHIP_FREEZE_CONFLICT"));

        mockMvc.perform(authed(get("/api/v1/membership-freezes"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        mockMvc.perform(authed(delete("/api/v1/membership-freezes/" + freezeId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/membership-freezes/" + freezeId), ownerToken)).andExpect(status().isNotFound());

        // --- A MEMBER teammate: MEMBERSHIP_FREEZE is a core CRM resource, so they hold CREATE:OWN by default. ---
        String teammateEmail = "freeze-teammate-%d@example.com".formatted(System.nanoTime());
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

        mockMvc.perform(authed(post("/api/v1/membership-freezes"), teammateToken)
                        .content(
                                """
                                {"membershipId":"%s","freezeStart":"2026-05-01","freezeEnd":"2026-05-10"}
                                """
                                        .formatted(membershipId)))
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
