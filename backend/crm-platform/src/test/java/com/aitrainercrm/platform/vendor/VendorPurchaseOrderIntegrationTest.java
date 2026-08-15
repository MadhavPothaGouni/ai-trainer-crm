package com.aitrainercrm.platform.vendor;

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
 * End-to-end coverage for the vendor catalog and its owner-scoped purchase orders - see V47's
 * migration comment. Mirrors {@code EquipmentIntegrationTest}'s shape for the catalog half
 * (VENDOR isn't a core CRM resource, same as PRODUCT/EQUIPMENT) and {@code ShiftIntegrationTest}'s
 * shape for the owner-scoped order half, including receivedAt stamped once via PATCH .../status.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class VendorPurchaseOrderIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void vendorCatalogAndPurchaseOrderLifecycle_endToEnd() throws Exception {
        String ownerToken = registerOwner("vendor-crud");

        MvcResult vendorResult = mockMvc
                .perform(authed(post("/api/v1/vendors"), ownerToken)
                        .content("{\"name\":\"Acme Supply Co\",\"contactName\":\"Jordan Rep\",\"email\":\"jordan@acmesupply.example\",\"category\":\"Equipment\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.active").value(true))
                .andReturn();
        String vendorId = readField(vendorResult, "data", "id");
        assertThat(vendorId).isNotBlank();

        MvcResult orderResult = mockMvc
                .perform(authed(post("/api/v1/purchase-orders"), ownerToken)
                        .content("{\"vendorId\":\"%s\",\"orderDate\":\"2026-02-01\",\"totalAmount\":499.99}".formatted(vendorId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.receivedAt").doesNotExist())
                .andReturn();
        String orderId = readField(orderResult, "data", "id");
        assertThat(orderId).isNotBlank();

        mockMvc.perform(authed(patch("/api/v1/purchase-orders/" + orderId + "/status"), ownerToken).content("{\"status\":\"ORDERED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ORDERED"));

        MvcResult receivedResult = mockMvc
                .perform(authed(patch("/api/v1/purchase-orders/" + orderId + "/status"), ownerToken).content("{\"status\":\"RECEIVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RECEIVED"))
                .andExpect(jsonPath("$.data.receivedAt").exists())
                .andReturn();
        String receivedAt = readField(receivedResult, "data", "receivedAt");

        // A later correction back through ORDERED and to RECEIVED again must not move receivedAt.
        mockMvc.perform(authed(patch("/api/v1/purchase-orders/" + orderId + "/status"), ownerToken).content("{\"status\":\"ORDERED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(authed(patch("/api/v1/purchase-orders/" + orderId + "/status"), ownerToken).content("{\"status\":\"RECEIVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receivedAt").value(receivedAt));

        mockMvc.perform(authed(put("/api/v1/vendors/" + vendorId), ownerToken)
                        .content("{\"name\":\"Acme Supply Co\",\"category\":\"Equipment\",\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));

        mockMvc.perform(authed(get("/api/v1/purchase-orders"), ownerToken)).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(authed(delete("/api/v1/purchase-orders/" + orderId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/purchase-orders/" + orderId), ownerToken)).andExpect(status().isNotFound());

        // --- A MEMBER teammate: VENDOR isn't a core CRM resource, PURCHASE_ORDER is. ---
        String teammateEmail = "vendor-teammate-%d@example.com".formatted(System.nanoTime());
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

        mockMvc.perform(authed(post("/api/v1/vendors"), teammateToken).content("{\"name\":\"Unauthorized Vendor\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(authed(post("/api/v1/purchase-orders"), teammateToken)
                        .content("{\"vendorId\":\"%s\",\"orderDate\":\"2026-02-05\"}".formatted(vendorId)))
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
