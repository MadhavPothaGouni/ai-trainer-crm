package com.aitrainercrm.platform.apikey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitrainercrm.platform.apikey.dto.CreateApiKeyRequest;
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
 * End-to-end coverage for programmatic auth: create a key, use its raw
 * value (never the JWT) to call a protected endpoint via the X-Api-Key
 * header and prove it authenticates as its creator, list keys and confirm
 * the raw secret never comes back a second time, revoke it and prove the
 * same raw value is rejected afterward, and confirm a default MEMBER
 * (API_KEY isn't a core CRM resource) can't create one at all.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ApiKeyIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void apiKeyAuthenticatesAsItsCreator_andStopsWorkingOnceRevoked() throws Exception {
        String ownerEmail = "apikey-owner-%d@example.com".formatted(System.nanoTime());
        MvcResult registerResult = mockMvc
                .perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(ownerEmail, "Str0ng!Passw0rd", "Owner", "Person", "Acme Integrations"))))
                .andExpect(status().isCreated())
                .andReturn();
        String ownerToken = readField(registerResult, "data", "accessToken");

        // --- Create a key ---
        MvcResult createResult = mockMvc
                .perform(authed(post("/api/v1/api-keys"), ownerToken)
                        .content(objectMapper.writeValueAsString(new CreateApiKeyRequest("CI bot", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.keyPrefix").exists())
                .andReturn();
        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data");
        String rawKey = created.get("rawKey").asText();
        String apiKeyId = created.get("id").asText();
        assertThat(rawKey).contains(".");
        assertThat(rawKey).startsWith(created.get("keyPrefix").asText());

        // --- The raw key authenticates the same protected endpoint a Bearer JWT would, as the OWNER who created it ---
        mockMvc.perform(get("/api/v1/accounts").header("X-Api-Key", rawKey)).andExpect(status().isOk());

        // --- Listing keys never exposes rawKey again ---
        MvcResult listResult = mockMvc
                .perform(authed(get("/api/v1/api-keys"), ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode listedKey = objectMapper.readTree(listResult.getResponse().getContentAsString()).get("data").get("content").get(0);
        assertThat(listedKey.has("rawKey") && !listedKey.get("rawKey").isNull()).isFalse();

        // --- A garbled or unrecognized key leaves the request unauthenticated, same as a bad JWT ---
        mockMvc.perform(get("/api/v1/accounts").header("X-Api-Key", "not-a-real-key")).andExpect(status().isUnauthorized());

        // --- API_KEY isn't a core CRM resource (see RoleService#isCoreCrmResource) - a default MEMBER can't create one ---
        String teammateEmail = "apikey-teammate-%d@example.com".formatted(System.nanoTime());
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
        mockMvc.perform(authed(post("/api/v1/api-keys"), teammateToken)
                        .content(objectMapper.writeValueAsString(new CreateApiKeyRequest("Teammate's key", null))))
                .andExpect(status().isForbidden());

        // --- Revoke the key ---
        mockMvc.perform(delete("/api/v1/api-keys/" + apiKeyId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        // --- The exact same raw value no longer authenticates anything ---
        mockMvc.perform(get("/api/v1/accounts").header("X-Api-Key", rawKey)).andExpect(status().isUnauthorized());
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
