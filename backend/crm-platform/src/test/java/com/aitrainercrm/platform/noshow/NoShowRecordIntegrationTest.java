package com.aitrainercrm.platform.noshow;

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
import java.time.Instant;
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
 * End-to-end coverage for no-show records - see V58's migration comment. Owner-scoped, so
 * NO_SHOW_RECORD (like GIFT_CARD, unlike catalog-only resources) grants MEMBER teammates OWN-scope
 * access by default; the interesting behavior is {@code NoShowRecordService#waive}'s business-rule
 * rejections, mirrored here the same way {@code GiftCardIntegrationTest} covers {@code redeem}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class NoShowRecordIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void noShowRecordLifecycle_endToEnd() throws Exception {
        String ownerToken = registerOwner("noshow-crud");

        MvcResult contactResult = mockMvc
                .perform(authed(post("/api/v1/contacts"), ownerToken).content("{\"firstName\":\"Jamie\",\"lastName\":\"Client\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String contactId = readField(contactResult, "data", "id");

        String occurredAt = Instant.now().toString();
        MvcResult createResult = mockMvc
                .perform(authed(post("/api/v1/no-show-records"), ownerToken)
                        .content("{\"contactId\":\"%s\",\"occurredAt\":\"%s\",\"relatedType\":\"CLASS_SESSION\",\"feeAmount\":25.00}"
                                .formatted(contactId, occurredAt)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.relatedType").value("CLASS_SESSION"))
                .andExpect(jsonPath("$.data.feeAmount").value(25.00))
                .andExpect(jsonPath("$.data.waived").value(false))
                .andReturn();
        String noShowRecordId = readField(createResult, "data", "id");
        assertThat(noShowRecordId).isNotBlank();

        // Waiving stamps waivedAt and flips waived to true.
        MvcResult waiveResult = mockMvc
                .perform(authed(post("/api/v1/no-show-records/" + noShowRecordId + "/waive"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.waived").value(true))
                .andExpect(jsonPath("$.data.waivedAt").exists())
                .andReturn();
        assertThat(readField(waiveResult, "data", "waivedAt")).isNotBlank();

        // Waiving an already-waived record is rejected.
        mockMvc.perform(authed(post("/api/v1/no-show-records/" + noShowRecordId + "/waive"), ownerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("NO_SHOW_RECORD_ALREADY_WAIVED"));

        // A record with no fee can't be waived.
        MvcResult noFeeResult = mockMvc
                .perform(authed(post("/api/v1/no-show-records"), ownerToken)
                        .content("{\"contactId\":\"%s\",\"occurredAt\":\"%s\",\"relatedType\":\"OTHER\"}".formatted(contactId, occurredAt)))
                .andExpect(status().isCreated())
                .andReturn();
        String noFeeRecordId = readField(noFeeResult, "data", "id");
        mockMvc.perform(authed(post("/api/v1/no-show-records/" + noFeeRecordId + "/waive"), ownerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("NO_SHOW_RECORD_NO_FEE"));

        // Editing works and doesn't touch the waived flag.
        mockMvc.perform(authed(put("/api/v1/no-show-records/" + noShowRecordId), ownerToken)
                        .content("{\"occurredAt\":\"%s\",\"relatedType\":\"TRAINING_SESSION\",\"feeAmount\":30.00,\"notes\":\"Rescheduled fee\"}"
                                .formatted(occurredAt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.relatedType").value("TRAINING_SESSION"))
                .andExpect(jsonPath("$.data.feeAmount").value(30.00))
                .andExpect(jsonPath("$.data.waived").value(true));

        mockMvc.perform(authed(get("/api/v1/no-show-records"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        mockMvc.perform(authed(delete("/api/v1/no-show-records/" + noShowRecordId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/no-show-records/" + noShowRecordId), ownerToken)).andExpect(status().isNotFound());

        // --- A MEMBER teammate: NO_SHOW_RECORD is owner-scoped, so they can create their own but not see this one. ---
        String teammateEmail = "noshow-teammate-%d@example.com".formatted(System.nanoTime());
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

        mockMvc.perform(authed(post("/api/v1/no-show-records"), teammateToken)
                        .content("{\"contactId\":\"%s\",\"occurredAt\":\"%s\",\"relatedType\":\"OTHER\"}".formatted(contactId, occurredAt)))
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
