package com.aitrainercrm.platform.locker;

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
 * End-to-end coverage for the locker catalog and its owner-scoped assignments - see V50's
 * migration comment. Mirrors {@code VendorPurchaseOrderIntegrationTest}'s shape for the catalog
 * half (LOCKER isn't a core CRM resource, same as VENDOR/EQUIPMENT) and the owner-scoped
 * assignment half, including returnedAt stamped once via PATCH .../status.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class LockerAssignmentIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void lockerCatalogAndAssignmentLifecycle_endToEnd() throws Exception {
        String ownerToken = registerOwner("locker-crud");

        MvcResult lockerResult = mockMvc
                .perform(authed(post("/api/v1/lockers"), ownerToken).content("{\"label\":\"A-12\",\"location\":\"Men's locker room\",\"size\":\"MEDIUM\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn();
        String lockerId = readField(lockerResult, "data", "id");
        assertThat(lockerId).isNotBlank();

        MvcResult contactResult = mockMvc
                .perform(authed(post("/api/v1/contacts"), ownerToken).content("{\"firstName\":\"Jamie\",\"lastName\":\"Client\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String contactId = readField(contactResult, "data", "id");

        MvcResult assignmentResult = mockMvc
                .perform(authed(post("/api/v1/locker-assignments"), ownerToken)
                        .content("{\"lockerId\":\"%s\",\"contactId\":\"%s\",\"expiresAt\":\"2026-12-31\"}".formatted(lockerId, contactId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.returnedAt").doesNotExist())
                .andReturn();
        String assignmentId = readField(assignmentResult, "data", "id");
        assertThat(assignmentId).isNotBlank();

        MvcResult returnedResult = mockMvc
                .perform(authed(patch("/api/v1/locker-assignments/" + assignmentId + "/status"), ownerToken).content("{\"status\":\"RETURNED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RETURNED"))
                .andExpect(jsonPath("$.data.returnedAt").exists())
                .andReturn();
        String returnedAt = readField(returnedResult, "data", "returnedAt");

        // A later correction back through ACTIVE and to RETURNED again must not move returnedAt.
        mockMvc.perform(authed(patch("/api/v1/locker-assignments/" + assignmentId + "/status"), ownerToken).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());
        MvcResult reReturnedResult = mockMvc
                .perform(authed(patch("/api/v1/locker-assignments/" + assignmentId + "/status"), ownerToken).content("{\"status\":\"RETURNED\"}"))
                .andExpect(status().isOk())
                .andReturn();
        // Compared via Instant equality (not raw string/jsonPath equality) - the first response
        // returns the in-memory Instant.now() at full nanosecond precision, while this later
        // response was re-fetched from Postgres, whose timestamptz column only holds microsecond
        // precision, so the two ISO strings can legitimately differ in their last digit while still
        // being the same instant.
        assertThat(Instant.parse(readField(reReturnedResult, "data", "returnedAt")))
                .isCloseTo(Instant.parse(returnedAt), org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.MICROS));

        mockMvc.perform(authed(put("/api/v1/lockers/" + lockerId), ownerToken)
                        .content("{\"label\":\"A-12\",\"size\":\"MEDIUM\",\"status\":\"OUT_OF_SERVICE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OUT_OF_SERVICE"));

        mockMvc.perform(authed(get("/api/v1/locker-assignments"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(authed(delete("/api/v1/locker-assignments/" + assignmentId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/locker-assignments/" + assignmentId), ownerToken)).andExpect(status().isNotFound());

        // --- A MEMBER teammate: LOCKER isn't a core CRM resource, LOCKER_ASSIGNMENT is. ---
        String teammateEmail = "locker-teammate-%d@example.com".formatted(System.nanoTime());
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

        mockMvc.perform(authed(post("/api/v1/lockers"), teammateToken).content("{\"label\":\"Unauthorized\",\"size\":\"SMALL\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(authed(post("/api/v1/locker-assignments"), teammateToken)
                        .content("{\"lockerId\":\"%s\",\"contactId\":\"%s\"}".formatted(lockerId, contactId)))
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
