package com.aitrainercrm.platform.loyalty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * End-to-end coverage for loyalty transactions - see V59's migration comment. Owner-scoped, so
 * LOYALTY_TRANSACTION (like GIFT_CARD/NO_SHOW_RECORD) grants MEMBER teammates OWN-scope access by
 * default; the interesting behavior is {@code LoyaltyTransactionService#assertSignMatchesReason}'s
 * rejection and {@code #getBalance}'s live aggregation, mirrored here the same way
 * {@code GiftCardIntegrationTest} covers {@code redeem}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class LoyaltyTransactionIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void loyaltyTransactionLifecycle_endToEnd() throws Exception {
        String ownerToken = registerOwner("loyalty-crud");

        MvcResult contactResult = mockMvc
                .perform(authed(post("/api/v1/contacts"), ownerToken).content("{\"firstName\":\"Jamie\",\"lastName\":\"Client\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String contactId = readField(contactResult, "data", "id");

        // Earning points with a negative delta is rejected.
        mockMvc.perform(authed(post("/api/v1/loyalty-transactions"), ownerToken)
                        .content("{\"contactId\":\"%s\",\"points\":-10,\"reason\":\"EARNED_CHECKIN\"}".formatted(contactId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("LOYALTY_TRANSACTION_INVALID_SIGN"));

        // Earning 10 points for a check-in.
        mockMvc.perform(authed(post("/api/v1/loyalty-transactions"), ownerToken)
                        .content("{\"contactId\":\"%s\",\"points\":10,\"reason\":\"EARNED_CHECKIN\"}".formatted(contactId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.points").value(10));

        // Earning 15 more points for a referral.
        mockMvc.perform(authed(post("/api/v1/loyalty-transactions"), ownerToken)
                        .content("{\"contactId\":\"%s\",\"points\":15,\"reason\":\"EARNED_REFERRAL\"}".formatted(contactId)))
                .andExpect(status().isCreated());

        // Redeeming with a positive delta is rejected.
        mockMvc.perform(authed(post("/api/v1/loyalty-transactions"), ownerToken)
                        .content("{\"contactId\":\"%s\",\"points\":5,\"reason\":\"REDEEMED_REWARD\"}".formatted(contactId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("LOYALTY_TRANSACTION_INVALID_SIGN"));

        // Redeeming 8 points for a reward.
        MvcResult redeemResult = mockMvc
                .perform(authed(post("/api/v1/loyalty-transactions"), ownerToken)
                        .content("{\"contactId\":\"%s\",\"points\":-8,\"reason\":\"REDEEMED_REWARD\"}".formatted(contactId)))
                .andExpect(status().isCreated())
                .andReturn();
        String redeemedTransactionId = readField(redeemResult, "data", "id");

        // Balance is 10 + 15 - 8 = 17.
        mockMvc.perform(authed(get("/api/v1/loyalty-transactions/balance/" + contactId), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(17));

        // Deleting the redemption brings the balance back up to 25.
        mockMvc.perform(authed(delete("/api/v1/loyalty-transactions/" + redeemedTransactionId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/loyalty-transactions/balance/" + contactId), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(25));

        mockMvc.perform(authed(get("/api/v1/loyalty-transactions"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        // --- A MEMBER teammate: LOYALTY_TRANSACTION is owner-scoped, so they can create their own and their balance query only sums their own entries. ---
        String teammateEmail = "loyalty-teammate-%d@example.com".formatted(System.nanoTime());
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

        mockMvc.perform(authed(post("/api/v1/loyalty-transactions"), teammateToken)
                        .content("{\"contactId\":\"%s\",\"points\":3,\"reason\":\"MANUAL_ADJUSTMENT\"}".formatted(contactId)))
                .andExpect(status().isCreated());

        // The teammate's OWN-scoped balance view only sees their own 3 points, not the owner's 25.
        mockMvc.perform(authed(get("/api/v1/loyalty-transactions/balance/" + contactId), teammateToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(3));

        // The organization-scoped owner still sees the true total of 25 + 3 = 28.
        mockMvc.perform(authed(get("/api/v1/loyalty-transactions/balance/" + contactId), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(28));
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
