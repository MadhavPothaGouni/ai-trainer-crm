package com.aitrainercrm.platform.organization.controller;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitrainercrm.platform.auth.dto.RegisterRequest;
import com.aitrainercrm.platform.organization.dto.UpdateOrganizationRequest;
import com.aitrainercrm.platform.support.AbstractIntegrationTest;
import com.aitrainercrm.platform.user.dto.CreateUserRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Registers a brand-new organization through the real /auth/register
 * endpoint (exactly what a real signup does), then drives the
 * Organization/User/Role endpoints as that org's OWNER through real HTTP -
 * covering the two things a pure-Mockito test structurally can't:
 * @PreAuthorize authority strings actually matching what V2's permission
 * catalog seeds, and multi-tenant scoping (an org only ever sees its own
 * data) working end to end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class OrganizationUserRoleIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void ownerCanManageOrganizationUsersAndRoles_endToEnd() throws Exception {
        String ownerEmail = "owner-%d@example.com".formatted(System.nanoTime());
        RegisterRequest registerRequest =
                new RegisterRequest(ownerEmail, "Str0ng!Passw0rd", "Owner", "Person", "Acme Corp");

        MvcResult registerResult = mockMvc
                .perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String accessToken = readField(registerResult, "data", "accessToken");

        // GET /organizations/me reflects the org created during registration.
        mockMvc.perform(authed(get("/api/v1/organizations/me"), accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Acme Corp"));

        // OWNER can rename it.
        mockMvc.perform(authed(patch("/api/v1/organizations/me"), accessToken)
                        .content(objectMapper.writeValueAsString(new UpdateOrganizationRequest("Acme Corporation", "USD", "UTC", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Acme Corporation"));

        // GET /users/me is the registering OWNER.
        mockMvc.perform(authed(get("/api/v1/users/me"), accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(ownerEmail))
                .andExpect(jsonPath("$.data.roles[0]").value("OWNER"));

        // Three system roles (OWNER/ADMIN/MEMBER) exist from organization creation.
        mockMvc.perform(authed(get("/api/v1/roles"), accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(3)));

        // Inviting a teammate defaults them to MEMBER and doesn't require a password.
        String teammateEmail = "teammate-%d@example.com".formatted(System.nanoTime());
        MvcResult inviteResult = mockMvc
                .perform(authed(post("/api/v1/users"), accessToken)
                        .content(objectMapper.writeValueAsString(new CreateUserRequest(teammateEmail, "New", "Teammate", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roles[0]").value("MEMBER"))
                .andReturn();
        String teammateId = readField(inviteResult, "data", "id");

        // The org now has two users.
        mockMvc.perform(authed(get("/api/v1/users"), accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        // A system role (OWNER) can never be deleted, even by the OWNER.
        MvcResult rolesResult = mockMvc.perform(authed(get("/api/v1/roles"), accessToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode roles = objectMapper.readTree(rolesResult.getResponse().getContentAsString()).get("data");
        String ownerRoleId = null;
        for (JsonNode role : roles) {
            if ("OWNER".equals(role.get("name").asText())) {
                ownerRoleId = role.get("id").asText();
            }
        }
        mockMvc.perform(authed(delete("/api/v1/roles/" + ownerRoleId), accessToken))
                .andExpect(status().isForbidden());

        // The sole OWNER can't be demoted or removed - would leave the org with zero owners.
        MvcResult selfResult = mockMvc.perform(authed(get("/api/v1/users/me"), accessToken)).andReturn();
        String ownerId = readField(selfResult, "data", "id");
        mockMvc.perform(authed(delete("/api/v1/users/" + ownerId), accessToken))
                .andExpect(status().isForbidden());

        // But the OWNER can remove the teammate they just invited.
        mockMvc.perform(authed(delete("/api/v1/users/" + teammateId), accessToken))
                .andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/users"), accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder, String accessToken) {
        return builder.header("Authorization", "Bearer " + accessToken).contentType(MediaType.APPLICATION_JSON);
    }

    private String readField(MvcResult result, String... path) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        for (String segment : path) {
            node = node.get(segment);
        }
        return node.asText();
    }
}
