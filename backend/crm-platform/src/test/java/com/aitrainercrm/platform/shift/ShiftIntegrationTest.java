package com.aitrainercrm.platform.shift;

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
 * End-to-end coverage for the shift-pattern catalog and owner-scoped shifts - see V45's migration
 * comment. Mirrors {@code MembershipIntegrationTest}'s shape (SHIFT_TEMPLATE isn't a core CRM
 * resource, same as PRODUCT/GROUP_CLASS) plus clock-in/out stamp-once behavior.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ShiftIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void shiftTemplateCatalogAndShiftLifecycle_endToEnd() throws Exception {
        String ownerToken = registerOwner("shift-crud");

        MvcResult templateResult = mockMvc
                .perform(authed(post("/api/v1/shift-templates"), ownerToken)
                        .content("{\"name\":\"Front Desk - Weekday Mornings\",\"dayOfWeek\":\"MONDAY\",\"startTime\":\"07:00:00\",\"endTime\":\"12:00:00\",\"role\":\"Front Desk\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.active").value(true))
                .andReturn();
        String templateId = readField(templateResult, "data", "id");
        assertThat(templateId).isNotBlank();

        MvcResult shiftResult = mockMvc
                .perform(authed(post("/api/v1/shifts"), ownerToken)
                        .content(
                                """
                                {"shiftTemplateId":"%s","shiftDate":"2026-02-02","startsAt":"2026-02-02T07:00:00Z","endsAt":"2026-02-02T12:00:00Z"}
                                """
                                        .formatted(templateId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andReturn();
        String shiftId = readField(shiftResult, "data", "id");
        assertThat(shiftId).isNotBlank();

        // --- Clocking in stamps clockInAt once ---
        MvcResult clockInResult = mockMvc
                .perform(authed(patch("/api/v1/shifts/" + shiftId + "/status"), ownerToken).content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.clockInAt").exists())
                .andReturn();
        String clockInAt = readField(clockInResult, "data", "clockInAt");

        // --- Clocking out stamps clockOutAt once; a later correction doesn't move clockInAt ---
        MvcResult clockOutResult = mockMvc
                .perform(authed(patch("/api/v1/shifts/" + shiftId + "/status"), ownerToken).content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.clockOutAt").exists())
                .andReturn();
        assertThat(readField(clockOutResult, "data", "clockInAt")).isEqualTo(clockInAt);

        mockMvc.perform(authed(get("/api/v1/shifts"), ownerToken)).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(authed(delete("/api/v1/shifts/" + shiftId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/shifts/" + shiftId), ownerToken)).andExpect(status().isNotFound());

        // --- A MEMBER teammate: SHIFT_TEMPLATE isn't a core CRM resource, SHIFT is. ---
        String teammateEmail = "shift-teammate-%d@example.com".formatted(System.nanoTime());
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

        mockMvc.perform(authed(post("/api/v1/shift-templates"), teammateToken)
                        .content("{\"name\":\"Unauthorized\",\"dayOfWeek\":\"TUESDAY\",\"startTime\":\"09:00:00\",\"endTime\":\"17:00:00\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(authed(post("/api/v1/shifts"), teammateToken)
                        .content("{\"shiftDate\":\"2026-02-03\",\"startsAt\":\"2026-02-03T09:00:00Z\",\"endsAt\":\"2026-02-03T17:00:00Z\"}"))
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
