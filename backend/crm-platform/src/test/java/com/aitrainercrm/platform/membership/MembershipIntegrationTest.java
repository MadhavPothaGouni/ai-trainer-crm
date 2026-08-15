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
 * End-to-end coverage for Membership Plans (the shared catalog) and Memberships (a client's
 * actual owner-scoped subscription) - see V42's migration comment and {@code Membership}'s
 * javadoc for the gap this fills. Mirrors {@code ProductQuoteIntegrationTest}'s shape for the
 * plan-catalog half (a MEMBER holds no MEMBERSHIP_PLAN authority by default, same as PRODUCT)
 * and {@code ClientGoalIntegrationTest}'s shape for the owner-scoped membership half, plus the
 * one piece of real business logic neither of those covers: a membership snapshots its plan's
 * price/session credits at creation time and keeps them even after the plan later changes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class MembershipIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void planCatalogAndMembershipLifecycle_endToEnd() throws Exception {
        String ownerToken = registerOwner("membership-crud");
        String contactId = createContact(ownerToken, "Jamie", "Client");

        // --- Membership plan (catalog) ---
        MvcResult planResult = mockMvc
                .perform(authed(post("/api/v1/membership-plans"), ownerToken)
                        .content(
                                """
                                {"name":"Unlimited Monthly","description":"Unlimited classes, billed monthly",
                                "billingCycle":"MONTHLY","price":149.00,"currency":"USD","sessionCredits":null}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.active").value(true))
                .andReturn();
        String planId = readField(planResult, "data", "id");
        assertThat(planId).isNotBlank();

        // --- Membership, snapshotting the plan's price at creation ---
        MvcResult createResult = mockMvc
                .perform(authed(post("/api/v1/memberships"), ownerToken)
                        .content(
                                """
                                {"contactId":"%s","membershipPlanId":"%s","startDate":"2026-01-01",
                                "nextBillingDate":"2026-02-01","autoRenew":true,"notes":"Signed up at the front desk"}
                                """
                                        .formatted(contactId, planId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.billingCyclePrice").value(149.00))
                .andReturn();
        String membershipId = readField(createResult, "data", "id");
        assertThat(membershipId).isNotBlank();

        // --- The plan's price changing later must not retroactively re-bill the existing membership ---
        mockMvc.perform(authed(put("/api/v1/membership-plans/" + planId), ownerToken)
                        .content(
                                """
                                {"name":"Unlimited Monthly","description":"Price increase","billingCycle":"MONTHLY",
                                "price":179.00,"currency":"USD","sessionCredits":null,"active":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.price").value(179.00));

        mockMvc.perform(authed(get("/api/v1/memberships/" + membershipId), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.billingCyclePrice").value(149.00));

        // --- Pause, then reactivate: a free (non-linear) status model, same as tickets/contracts/client goals ---
        mockMvc.perform(authed(patch("/api/v1/memberships/" + membershipId + "/status"), ownerToken).content("{\"status\":\"PAUSED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAUSED"))
                .andExpect(jsonPath("$.data.pausedAt").exists());

        mockMvc.perform(authed(patch("/api/v1/memberships/" + membershipId + "/status"), ownerToken).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        // --- Cancelling clears nextBillingDate, since there's nothing left to bill ---
        mockMvc.perform(authed(patch("/api/v1/memberships/" + membershipId + "/status"), ownerToken).content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelledAt").exists())
                .andExpect(jsonPath("$.data.nextBillingDate").doesNotExist());

        mockMvc.perform(authed(get("/api/v1/memberships"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(authed(delete("/api/v1/memberships/" + membershipId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/memberships/" + membershipId), ownerToken)).andExpect(status().isNotFound());

        // --- A MEMBER teammate: MEMBERSHIP_PLAN isn't a core CRM resource (see
        // RoleService#isCoreCrmResource), so the default MEMBER role holds no
        // MEMBERSHIP_PLAN:CREATE authority at all - proves the "shared catalog, admin-managed"
        // design intent from V42's migration comment actually holds, same as PRODUCT. ---
        String teammateEmail = "membership-teammate-%d@example.com".formatted(System.nanoTime());
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

        mockMvc.perform(authed(post("/api/v1/membership-plans"), teammateToken)
                        .content("{\"name\":\"Unauthorized Plan\",\"billingCycle\":\"MONTHLY\",\"price\":10}"))
                .andExpect(status().isForbidden());

        // MEMBER does hold MEMBERSHIP:CREATE:OWN/TEAM though (MEMBERSHIP is a core CRM resource) -
        // null ownerId defaults to themselves.
        mockMvc.perform(authed(post("/api/v1/memberships"), teammateToken)
                        .content(
                                """
                                {"contactId":"%s","membershipPlanId":"%s","startDate":"2026-03-01","autoRenew":false}
                                """
                                        .formatted(contactId, planId)))
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
