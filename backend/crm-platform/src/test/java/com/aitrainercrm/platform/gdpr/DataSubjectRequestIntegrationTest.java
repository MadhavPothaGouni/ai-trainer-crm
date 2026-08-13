package com.aitrainercrm.platform.gdpr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitrainercrm.platform.auth.dto.LoginRequest;
import com.aitrainercrm.platform.auth.dto.RegisterRequest;
import com.aitrainercrm.platform.contact.dto.CreateContactRequest;
import com.aitrainercrm.platform.lead.dto.CreateLeadRequest;
import com.aitrainercrm.platform.lead.entity.Lead;
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
 * End-to-end coverage for what only real HTTP + real Postgres can pin down: a real export
 * actually returning the Contact/Lead data that exists for an email, a real erase actually
 * scrubbing those rows (verified through the ordinary GET endpoints returning 404 afterward, since
 * findActiveByIdAndOrganizationId excludes anything with deletedAt now set), erasure being
 * idempotent on a second call, and DATA_SUBJECT_REQUEST:*:ORGANIZATION actually gating every
 * endpoint against a bare MEMBER. {@code DataSubjectRequestServiceTest} covers the PII-scrubbing
 * and zero-match edge cases with mocks.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class DataSubjectRequestIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void export_returnsTheMatchingContactAndLead_asADownloadableJsonFile() throws Exception {
        String ownerToken = registerOwner("gdpr-export-owner");
        String email = "subject-%d@example.com".formatted(System.nanoTime());
        createContact(ownerToken, "Jane", "Doe", email);
        createLead(ownerToken, "Jane", "Doe", email);

        MvcResult result = mockMvc
                .perform(authed(post("/api/v1/data-subject-requests/export"), ownerToken).content("{\"subjectEmail\":\"" + email + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(body.get("subjectEmail").asText()).isEqualTo(email);
        assertThat(body.get("contacts")).hasSize(1);
        assertThat(body.get("contacts").get(0).get("firstName").asText()).isEqualTo("Jane");
        assertThat(body.get("leads")).hasSize(1);

        MvcResult historyResult =
                mockMvc.perform(authed(get("/api/v1/data-subject-requests"), ownerToken)).andExpect(status().isOk()).andReturn();
        JsonNode history = objectMapper.readTree(historyResult.getResponse().getContentAsString()).get("data").get("content");
        assertThat(history).hasSize(1);
        assertThat(history.get(0).get("requestType").asText()).isEqualTo("EXPORT");
        assertThat(history.get(0).get("contactsAffected").asInt()).isEqualTo(1);
    }

    @Test
    void erase_scrubsMatchingRecords_andIsIdempotentOnASecondCall() throws Exception {
        String ownerToken = registerOwner("gdpr-erase-owner");
        String email = "erase-%d@example.com".formatted(System.nanoTime());
        String contactId = createContact(ownerToken, "John", "Smith", email);
        createLead(ownerToken, "John", "Smith", email);

        mockMvc.perform(authed(get("/api/v1/contacts/" + contactId), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));

        MvcResult firstErase = mockMvc
                .perform(authed(post("/api/v1/data-subject-requests/erase"), ownerToken).content("{\"subjectEmail\":\"" + email + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode firstBody = objectMapper.readTree(firstErase.getResponse().getContentAsString()).get("data");
        assertThat(firstBody.get("contactsAffected").asInt()).isEqualTo(1);
        assertThat(firstBody.get("leadsAffected").asInt()).isEqualTo(1);
        assertThat(firstBody.get("requestType").asText()).isEqualTo("ERASURE");

        // The Contact is now soft-deleted (its PII scrubbed) - the ordinary GET, which only ever
        // resolves active rows, can no longer find it. Right to be forgotten, achieved with no
        // special-case code in ContactController/ContactService at all.
        mockMvc.perform(authed(get("/api/v1/contacts/" + contactId), ownerToken)).andExpect(status().isNotFound());

        // A second erase request for the same (now-scrubbed) email finds nothing left to affect.
        MvcResult secondErase = mockMvc
                .perform(authed(post("/api/v1/data-subject-requests/erase"), ownerToken).content("{\"subjectEmail\":\"" + email + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode secondBody = objectMapper.readTree(secondErase.getResponse().getContentAsString()).get("data");
        assertThat(secondBody.get("contactsAffected").asInt()).isZero();
        assertThat(secondBody.get("leadsAffected").asInt()).isZero();
    }

    @Test
    void bareMember_cannotUseAnyDataSubjectRequestEndpoint() throws Exception {
        String ownerToken = registerOwner("gdpr-permission-owner");
        String[] member = inviteAndLogin(ownerToken, "gdpr-permission-member");

        mockMvc.perform(authed(get("/api/v1/data-subject-requests"), member[1])).andExpect(status().isForbidden());
        mockMvc.perform(authed(post("/api/v1/data-subject-requests/export"), member[1]).content("{\"subjectEmail\":\"x@example.com\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(authed(post("/api/v1/data-subject-requests/erase"), member[1]).content("{\"subjectEmail\":\"x@example.com\"}"))
                .andExpect(status().isForbidden());
    }

    private String createContact(String token, String firstName, String lastName, String email) throws Exception {
        MvcResult result = mockMvc
                .perform(authed(post("/api/v1/contacts"), token)
                        .content(objectMapper.writeValueAsString(
                                new CreateContactRequest(firstName, lastName, email, null, null, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return readField(result, "data", "id");
    }

    private String createLead(String token, String firstName, String lastName, String email) throws Exception {
        MvcResult result = mockMvc
                .perform(authed(post("/api/v1/leads"), token)
                        .content(objectMapper.writeValueAsString(
                                new CreateLeadRequest(firstName, lastName, email, null, null, null, Lead.Source.WEBSITE, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return readField(result, "data", "id");
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

    /** @return {userId, accessToken} for a freshly invited MEMBER teammate in the caller's org. */
    private String[] inviteAndLogin(String ownerToken, String prefix) throws Exception {
        String email = "%s-%d@example.com".formatted(prefix, System.nanoTime());
        mockMvc.perform(authed(post("/api/v1/users"), ownerToken)
                        .content(objectMapper.writeValueAsString(new CreateUserRequest(email, "New", "Teammate", null))))
                .andExpect(status().isCreated());

        String password = "Str0ng!Passw0rd2";
        User teammate = userRepository.findByEmailAndDeletedAtIsNull(email.trim().toLowerCase()).orElseThrow();
        teammate.setPasswordHash(passwordEncoder.encode(password));
        userRepository.save(teammate);

        MvcResult loginResult = mockMvc
                .perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();
        return new String[] {readField(loginResult, "data", "userId"), readField(loginResult, "data", "accessToken")};
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
