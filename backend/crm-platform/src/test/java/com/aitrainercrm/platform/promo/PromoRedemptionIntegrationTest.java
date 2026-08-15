package com.aitrainercrm.platform.promo;

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
 * End-to-end coverage for the promo code catalog and its owner-scoped redemptions - see V51's
 * migration comment. Mirrors {@code LockerAssignmentIntegrationTest}'s shape for the catalog half
 * (PROMO_CODE isn't a core CRM resource, same as LOCKER/VENDOR) and the owner-scoped redemption
 * half, plus the redeemability checks (inactive/expired/redemption-cap) {@code
 * PromoRedemptionService#assertRedeemable} enforces before a redemption is recorded.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class PromoRedemptionIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void promoCodeCatalogAndRedemptionLifecycle_endToEnd() throws Exception {
        String ownerToken = registerOwner("promo-crud");

        MvcResult promoCodeResult = mockMvc
                .perform(authed(post("/api/v1/promo-codes"), ownerToken)
                        .content("{\"code\":\"summer10\",\"discountType\":\"PERCENTAGE\",\"discountValue\":10,\"maxRedemptions\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("SUMMER10"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andReturn();
        String promoCodeId = readField(promoCodeResult, "data", "id");
        assertThat(promoCodeId).isNotBlank();

        MvcResult contactResult = mockMvc
                .perform(authed(post("/api/v1/contacts"), ownerToken).content("{\"firstName\":\"Jamie\",\"lastName\":\"Client\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String contactId = readField(contactResult, "data", "id");

        MvcResult redemptionResult = mockMvc
                .perform(authed(post("/api/v1/promo-redemptions"), ownerToken)
                        .content("{\"promoCodeId\":\"%s\",\"contactId\":\"%s\",\"amountDiscounted\":5.00}".formatted(promoCodeId, contactId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.amountDiscounted").value(5.00))
                .andReturn();
        String redemptionId = readField(redemptionResult, "data", "id");
        assertThat(redemptionId).isNotBlank();

        // The code's maxRedemptions of 1 has now been used - a second redemption is rejected.
        String secondContactId = readField(
                mockMvc.perform(authed(post("/api/v1/contacts"), ownerToken).content("{\"firstName\":\"Riley\",\"lastName\":\"Client\"}"))
                        .andExpect(status().isCreated())
                        .andReturn(),
                "data", "id");
        mockMvc.perform(authed(post("/api/v1/promo-redemptions"), ownerToken)
                        .content("{\"promoCodeId\":\"%s\",\"contactId\":\"%s\"}".formatted(promoCodeId, secondContactId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PROMO_CODE_REDEMPTION_LIMIT_REACHED"));

        mockMvc.perform(authed(put("/api/v1/promo-codes/" + promoCodeId), ownerToken)
                        .content("{\"code\":\"SUMMER10\",\"discountType\":\"PERCENTAGE\",\"discountValue\":10,\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));

        // Deactivating the code also rejects new redemptions on a fresh code with room to spare.
        MvcResult otherCodeResult = mockMvc
                .perform(authed(post("/api/v1/promo-codes"), ownerToken)
                        .content("{\"code\":\"INACTIVE1\",\"discountType\":\"FIXED_AMOUNT\",\"discountValue\":15}"))
                .andExpect(status().isCreated())
                .andReturn();
        String otherCodeId = readField(otherCodeResult, "data", "id");
        mockMvc.perform(authed(put("/api/v1/promo-codes/" + otherCodeId), ownerToken)
                        .content("{\"code\":\"INACTIVE1\",\"discountType\":\"FIXED_AMOUNT\",\"discountValue\":15,\"active\":false}"))
                .andExpect(status().isOk());
        mockMvc.perform(authed(post("/api/v1/promo-redemptions"), ownerToken)
                        .content("{\"promoCodeId\":\"%s\",\"contactId\":\"%s\"}".formatted(otherCodeId, contactId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PROMO_CODE_INACTIVE"));

        mockMvc.perform(authed(get("/api/v1/promo-redemptions"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(authed(delete("/api/v1/promo-redemptions/" + redemptionId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/promo-redemptions/" + redemptionId), ownerToken)).andExpect(status().isNotFound());

        // --- A MEMBER teammate: PROMO_CODE isn't a core CRM resource, PROMO_REDEMPTION is. ---
        String teammateEmail = "promo-teammate-%d@example.com".formatted(System.nanoTime());
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

        mockMvc.perform(authed(post("/api/v1/promo-codes"), teammateToken).content("{\"code\":\"UNAUTHORIZED\",\"discountType\":\"PERCENTAGE\",\"discountValue\":5}"))
                .andExpect(status().isForbidden());
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
