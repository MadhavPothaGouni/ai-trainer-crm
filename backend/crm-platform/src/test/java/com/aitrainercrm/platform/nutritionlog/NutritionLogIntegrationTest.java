package com.aitrainercrm.platform.nutritionlog;

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
 * End-to-end coverage for nutrition logs - see V63's migration comment. Point-in-time-fact shape
 * like {@code ProgressPhotoIntegrationTest}: no status field, no PATCH .../status endpoint, plus
 * the same MEMBER-teammate owner-scope split every other core-CRM occurrence resource covers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class NutritionLogIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void nutritionLogLifecycle_endToEnd() throws Exception {
        String ownerToken = registerOwner("nutrition-log-crud");

        MvcResult contactResult = mockMvc
                .perform(authed(post("/api/v1/contacts"), ownerToken).content("{\"firstName\":\"Jamie\",\"lastName\":\"Client\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String contactId = readField(contactResult, "data", "id");

        String loggedAt = Instant.now().toString();
        MvcResult logResult = mockMvc
                .perform(authed(post("/api/v1/nutrition-logs"), ownerToken)
                        .content(
                                """
                                {"contactId":"%s","loggedAt":"%s","mealType":"LUNCH","calories":650,
                                "proteinGrams":40.5,"carbGrams":60.0,"fatGrams":18.0}
                                """
                                        .formatted(contactId, loggedAt)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.mealType").value("LUNCH"))
                .andExpect(jsonPath("$.data.calories").value(650))
                .andReturn();
        String logId = readField(logResult, "data", "id");
        assertThat(logId).isNotBlank();

        mockMvc.perform(authed(put("/api/v1/nutrition-logs/" + logId), ownerToken)
                        .content(
                                """
                                {"loggedAt":"%s","mealType":"DINNER","calories":800,"notes":"Post-workout meal"}
                                """
                                        .formatted(loggedAt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mealType").value("DINNER"))
                .andExpect(jsonPath("$.data.calories").value(800));

        mockMvc.perform(authed(get("/api/v1/nutrition-logs"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(authed(delete("/api/v1/nutrition-logs/" + logId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/nutrition-logs/" + logId), ownerToken)).andExpect(status().isNotFound());

        // --- A MEMBER teammate: NUTRITION_LOG is a core CRM resource, so they hold CREATE:OWN by default. ---
        String teammateEmail = "nutrition-log-teammate-%d@example.com".formatted(System.nanoTime());
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

        mockMvc.perform(authed(post("/api/v1/nutrition-logs"), teammateToken)
                        .content("{\"contactId\":\"%s\",\"loggedAt\":\"%s\",\"mealType\":\"BREAKFAST\"}".formatted(contactId, loggedAt)))
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
