package com.aitrainercrm.platform.intakeform;

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
import java.util.UUID;
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
 * End-to-end coverage for the intake-form catalog and its owner-scoped submissions - see V60's
 * migration comment. Mirrors {@code RoomBookingIntegrationTest}'s shape for the catalog-plus-
 * occurrence split (INTAKE_FORM isn't a core CRM resource, INTAKE_FORM_SUBMISSION is), plus a
 * dedicated case for the parent-form validation {@code IntakeFormSubmissionService#create} adds.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class IntakeFormIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void intakeFormCatalogAndSubmissionLifecycle_endToEnd() throws Exception {
        String ownerToken = registerOwner("intake-crud");

        MvcResult formResult = mockMvc
                .perform(authed(post("/api/v1/intake-forms"), ownerToken)
                        .content("{\"title\":\"New Client Intake\",\"formType\":\"NEW_CLIENT\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.active").value(true))
                .andReturn();
        String formId = readField(formResult, "data", "id");
        assertThat(formId).isNotBlank();

        MvcResult contactResult = mockMvc
                .perform(authed(post("/api/v1/contacts"), ownerToken).content("{\"firstName\":\"Jamie\",\"lastName\":\"Client\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String contactId = readField(contactResult, "data", "id");

        // A submission against a form that doesn't exist is rejected.
        mockMvc.perform(authed(post("/api/v1/intake-form-submissions"), ownerToken)
                        .content("{\"formId\":\"%s\",\"contactId\":\"%s\"}".formatted(UUID.randomUUID(), contactId)))
                .andExpect(status().isNotFound());

        MvcResult submissionResult = mockMvc
                .perform(authed(post("/api/v1/intake-form-submissions"), ownerToken)
                        .content("{\"formId\":\"%s\",\"contactId\":\"%s\",\"responses\":\"{\\\"health\\\":\\\"none\\\"}\"}"
                                .formatted(formId, contactId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.formId").value(formId))
                .andReturn();
        String submissionId = readField(submissionResult, "data", "id");
        assertThat(submissionId).isNotBlank();

        mockMvc.perform(authed(put("/api/v1/intake-form-submissions/" + submissionId), ownerToken)
                        .content("{\"responses\":\"{\\\"health\\\":\\\"asthma\\\"}\",\"notes\":\"Follow up on inhaler\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.responses").value("{\"health\":\"asthma\"}"));

        mockMvc.perform(authed(put("/api/v1/intake-forms/" + formId), ownerToken)
                        .content("{\"title\":\"New Client Intake\",\"formType\":\"NEW_CLIENT\",\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));

        mockMvc.perform(authed(get("/api/v1/intake-form-submissions"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(authed(delete("/api/v1/intake-form-submissions/" + submissionId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/intake-form-submissions/" + submissionId), ownerToken)).andExpect(status().isNotFound());

        // --- A MEMBER teammate: INTAKE_FORM isn't a core CRM resource, INTAKE_FORM_SUBMISSION is. ---
        String teammateEmail = "intake-teammate-%d@example.com".formatted(System.nanoTime());
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

        mockMvc.perform(authed(post("/api/v1/intake-forms"), teammateToken).content("{\"title\":\"Unauthorized\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(authed(post("/api/v1/intake-form-submissions"), teammateToken)
                        .content("{\"formId\":\"%s\",\"contactId\":\"%s\"}".formatted(formId, contactId)))
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
