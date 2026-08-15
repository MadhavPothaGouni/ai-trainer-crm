package com.aitrainercrm.platform.equipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * End-to-end coverage for equipment reservations - see V56's migration comment. Reuses the
 * existing Equipment catalog (V44) rather than a new one, so this test creates equipment via the
 * existing /api/v1/equipment endpoint before reserving it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class EquipmentReservationIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void equipmentReservationLifecycle_endToEnd() throws Exception {
        String ownerToken = registerOwner("equip-reservation-crud");

        MvcResult equipmentResult = mockMvc
                .perform(authed(post("/api/v1/equipment"), ownerToken).content("{\"name\":\"Squat Rack\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String equipmentId = readField(equipmentResult, "data", "id");

        Instant startsAt = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant endsAt = startsAt.plus(1, ChronoUnit.HOURS);

        MvcResult reservationResult = mockMvc
                .perform(authed(post("/api/v1/equipment-reservations"), ownerToken)
                        .content("{\"equipmentId\":\"%s\",\"startsAt\":\"%s\",\"endsAt\":\"%s\"}".formatted(equipmentId, startsAt, endsAt)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andReturn();
        String reservationId = readField(reservationResult, "data", "id");
        assertThat(reservationId).isNotBlank();

        mockMvc.perform(authed(patch("/api/v1/equipment-reservations/" + reservationId + "/status"), ownerToken).content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        mockMvc.perform(authed(get("/api/v1/equipment-reservations"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(authed(delete("/api/v1/equipment-reservations/" + reservationId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/equipment-reservations/" + reservationId), ownerToken)).andExpect(status().isNotFound());

        // --- A MEMBER teammate: EQUIPMENT isn't a core CRM resource, EQUIPMENT_RESERVATION is. ---
        String teammateEmail = "equip-reservation-teammate-%d@example.com".formatted(System.nanoTime());
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

        mockMvc.perform(authed(post("/api/v1/equipment"), teammateToken).content("{\"name\":\"Unauthorized\"}"))
                .andExpect(status().isForbidden());

        Instant teammateStartsAt = endsAt;
        Instant teammateEndsAt = teammateStartsAt.plus(1, ChronoUnit.HOURS);
        mockMvc.perform(authed(post("/api/v1/equipment-reservations"), teammateToken)
                        .content("{\"equipmentId\":\"%s\",\"startsAt\":\"%s\",\"endsAt\":\"%s\"}".formatted(equipmentId, teammateStartsAt, teammateEndsAt)))
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
