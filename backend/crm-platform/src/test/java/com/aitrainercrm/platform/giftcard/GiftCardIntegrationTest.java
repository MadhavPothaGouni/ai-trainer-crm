package com.aitrainercrm.platform.giftcard;

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
 * End-to-end coverage for gift cards - see V54's migration comment. Owner-scoped, so GIFT_CARD
 * (unlike catalog-only resources such as ROOM/LOCKER/PROMO_CODE) grants MEMBER teammates OWN-scope
 * access by default; the interesting behavior is {@code GiftCardService#redeem}'s balance math and
 * business-rule rejections, mirrored here the same way {@code PromoRedemptionIntegrationTest}
 * covers {@code assertRedeemable}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class GiftCardIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void giftCardLifecycle_endToEnd() throws Exception {
        String ownerToken = registerOwner("giftcard-crud");

        MvcResult contactResult = mockMvc
                .perform(authed(post("/api/v1/contacts"), ownerToken).content("{\"firstName\":\"Jamie\",\"lastName\":\"Client\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String contactId = readField(contactResult, "data", "id");

        MvcResult giftCardResult = mockMvc
                .perform(authed(post("/api/v1/gift-cards"), ownerToken)
                        .content("{\"contactId\":\"%s\",\"code\":\"gc-birthday\",\"initialBalance\":100.00}".formatted(contactId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("GC-BIRTHDAY"))
                .andExpect(jsonPath("$.data.currentBalance").value(100.00))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn();
        String giftCardId = readField(giftCardResult, "data", "id");
        assertThat(giftCardId).isNotBlank();

        mockMvc.perform(authed(post("/api/v1/gift-cards/" + giftCardId + "/redeem"), ownerToken).content("{\"amount\":40.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentBalance").value(60.00))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        // Redeeming more than the remaining balance is rejected.
        mockMvc.perform(authed(post("/api/v1/gift-cards/" + giftCardId + "/redeem"), ownerToken).content("{\"amount\":1000.00}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("GIFT_CARD_INSUFFICIENT_BALANCE"));

        // Redeeming exactly the remainder moves the card to REDEEMED and stamps redeemedAt.
        MvcResult finalRedeemResult = mockMvc
                .perform(authed(post("/api/v1/gift-cards/" + giftCardId + "/redeem"), ownerToken).content("{\"amount\":60.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentBalance").value(0.00))
                .andExpect(jsonPath("$.data.status").value("REDEEMED"))
                .andExpect(jsonPath("$.data.redeemedAt").exists())
                .andReturn();
        assertThat(readField(finalRedeemResult, "data", "redeemedAt")).isNotBlank();

        // A fully-redeemed, no-longer-ACTIVE card can't be redeemed further.
        mockMvc.perform(authed(post("/api/v1/gift-cards/" + giftCardId + "/redeem"), ownerToken).content("{\"amount\":1.00}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("GIFT_CARD_NOT_ACTIVE"));

        mockMvc.perform(authed(get("/api/v1/gift-cards"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(authed(delete("/api/v1/gift-cards/" + giftCardId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/gift-cards/" + giftCardId), ownerToken)).andExpect(status().isNotFound());

        // --- A MEMBER teammate: GIFT_CARD is owner-scoped, so they can create their own but not see this one. ---
        String teammateEmail = "giftcard-teammate-%d@example.com".formatted(System.nanoTime());
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

        mockMvc.perform(authed(post("/api/v1/gift-cards"), teammateToken)
                        .content("{\"contactId\":\"%s\",\"code\":\"gc-teammate\",\"initialBalance\":25.00}".formatted(contactId)))
                .andExpect(status().isCreated());
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
