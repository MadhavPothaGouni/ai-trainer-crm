package com.aitrainercrm.platform.clientfeedback;

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
 * End-to-end coverage for client feedback - see V66's migration comment. Point-in-time-fact shape
 * like {@code NutritionLogIntegrationTest}: no status field, no PATCH .../status endpoint, plus a
 * check that an out-of-range npsScore is rejected by bean validation, plus the same
 * MEMBER-teammate owner-scope split every other core-CRM occurrence resource covers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ClientFeedbackIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void clientFeedbackLifecycle_endToEnd() throws Exception {
        String ownerToken = registerOwner("client-feedback-crud");

        MvcResult contactResult = mockMvc
                .perform(authed(post("/api/v1/contacts"), ownerToken).content("{\"firstName\":\"Jamie\",\"lastName\":\"Client\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String contactId = readField(contactResult, "data", "id");

        String submittedAt = Instant.now().toString();

        // An npsScore above 10 is rejected by bean validation.
        mockMvc.perform(authed(post("/api/v1/client-feedback"), ownerToken)
                        .content(
                                """
                                {"contactId":"%s","npsScore":11,"relatedType":"SESSION","submittedAt":"%s"}
                                """
                                        .formatted(contactId, submittedAt)))
                .andExpect(status().isBadRequest());

        MvcResult feedbackResult = mockMvc
                .perform(authed(post("/api/v1/client-feedback"), ownerToken)
                        .content(
                                """
                                {"contactId":"%s","npsScore":9,"relatedType":"SESSION","submittedAt":"%s","comments":"Loved the workout"}
                                """
                                        .formatted(contactId, submittedAt)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.npsScore").value(9))
                .andExpect(jsonPath("$.data.relatedType").value("SESSION"))
                .andReturn();
        String feedbackId = readField(feedbackResult, "data", "id");
        assertThat(feedbackId).isNotBlank();

        mockMvc.perform(authed(put("/api/v1/client-feedback/" + feedbackId), ownerToken)
                        .content(
                                """
                                {"npsScore":10,"relatedType":"GENERAL","submittedAt":"%s","comments":"Even better on reflection"}
                                """
                                        .formatted(submittedAt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.npsScore").value(10))
                .andExpect(jsonPath("$.data.relatedType").value("GENERAL"));

        mockMvc.perform(authed(get("/api/v1/client-feedback"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(authed(delete("/api/v1/client-feedback/" + feedbackId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/client-feedback/" + feedbackId), ownerToken)).andExpect(status().isNotFound());

        // --- A MEMBER teammate: CLIENT_FEEDBACK is a core CRM resource, so they hold CREATE:OWN by default. ---
        String teammateEmail = "client-feedback-teammate-%d@example.com".formatted(System.nanoTime());
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

        mockMvc.perform(authed(post("/api/v1/client-feedback"), teammateToken)
                        .content("{\"contactId\":\"%s\",\"npsScore\":8,\"relatedType\":\"CLASS\",\"submittedAt\":\"%s\"}".formatted(contactId, submittedAt)))
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
