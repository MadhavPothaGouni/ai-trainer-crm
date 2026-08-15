package com.aitrainercrm.platform.equipment;

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
 * End-to-end coverage for the equipment catalog and its owner-scoped maintenance history - see
 * V44's migration comment. Mirrors {@code MembershipIntegrationTest}'s shape for the catalog half
 * (EQUIPMENT isn't a core CRM resource, same as PRODUCT/MEMBERSHIP_PLAN) and
 * {@code ClientGoalIntegrationTest}'s shape for the owner-scoped log half - minus any status
 * PATCH endpoint, since neither resource needs one the way Membership/ClassSession do.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class EquipmentIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void equipmentCatalogAndMaintenanceHistory_endToEnd() throws Exception {
        String ownerToken = registerOwner("equipment-crud");

        // --- Equipment (catalog) ---
        MvcResult equipmentResult = mockMvc
                .perform(authed(post("/api/v1/equipment"), ownerToken)
                        .content(
                                """
                                {"name":"Treadmill #3","category":"Cardio","serialNumber":"TM-2024-003",
                                "location":"Main Floor","purchaseDate":"2024-06-01","purchasePrice":3200.00}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn();
        String equipmentId = readField(equipmentResult, "data", "id");
        assertThat(equipmentId).isNotBlank();

        // --- Status is a free state machine: goes out of service, then comes back ---
        mockMvc.perform(authed(put("/api/v1/equipment/" + equipmentId), ownerToken)
                        .content(
                                """
                                {"name":"Treadmill #3","category":"Cardio","serialNumber":"TM-2024-003",
                                "location":"Main Floor","status":"OUT_OF_SERVICE","purchaseDate":"2024-06-01","purchasePrice":3200.00}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OUT_OF_SERVICE"));

        // --- Maintenance log, ownerId defaults to the caller ---
        MvcResult logResult = mockMvc
                .perform(authed(post("/api/v1/maintenance-logs"), ownerToken)
                        .content(
                                """
                                {"equipmentId":"%s","performedAt":"2026-02-01T09:00:00Z","type":"REPAIR",
                                "cost":150.00,"notes":"Replaced drive belt","nextDueDate":"2026-08-01"}
                                """
                                        .formatted(equipmentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.type").value("REPAIR"))
                .andReturn();
        String logId = readField(logResult, "data", "id");
        assertThat(logId).isNotBlank();

        // --- Back in service after the repair ---
        mockMvc.perform(authed(put("/api/v1/equipment/" + equipmentId), ownerToken)
                        .content(
                                """
                                {"name":"Treadmill #3","category":"Cardio","serialNumber":"TM-2024-003",
                                "location":"Main Floor","status":"ACTIVE","purchaseDate":"2024-06-01","purchasePrice":3200.00}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(authed(get("/api/v1/maintenance-logs"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(authed(delete("/api/v1/maintenance-logs/" + logId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/maintenance-logs/" + logId), ownerToken)).andExpect(status().isNotFound());

        // --- A MEMBER teammate: EQUIPMENT isn't a core CRM resource (see RoleService#isCoreCrmResource),
        // so the default MEMBER role holds no EQUIPMENT:CREATE authority - same admin-managed catalog
        // design intent as PRODUCT/GROUP_CLASS. MAINTENANCE_LOG IS a core CRM resource, so MEMBER holds
        // CREATE:OWN and a null ownerId defaults to themselves. ---
        String teammateEmail = "equipment-teammate-%d@example.com".formatted(System.nanoTime());
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

        mockMvc.perform(authed(post("/api/v1/equipment"), teammateToken).content("{\"name\":\"Unauthorized Rack\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(authed(post("/api/v1/maintenance-logs"), teammateToken)
                        .content("{\"equipmentId\":\"%s\",\"performedAt\":\"2026-03-01T09:00:00Z\",\"type\":\"ROUTINE\"}".formatted(equipmentId)))
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
