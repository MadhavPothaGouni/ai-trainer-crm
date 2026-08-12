package com.aitrainercrm.platform.emailtemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitrainercrm.platform.account.dto.CreateAccountRequest;
import com.aitrainercrm.platform.auth.dto.RegisterRequest;
import com.aitrainercrm.platform.contact.dto.CreateContactRequest;
import com.aitrainercrm.platform.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * End-to-end coverage for what only a real HTTP round trip can pin down: full CRUD gated by
 * EMAIL_TEMPLATE:*:ORGANIZATION (no OWN scope exists, same shape ProductIntegrationTest-style tests
 * use elsewhere), and a real render call merging real Contact and Account rows. {@code
 * EmailTemplateServiceTest} and {@code TemplateRendererTest} cover the token-substitution edge
 * cases (case-insensitivity, unresolved tokens, cross-org isolation) with mocks/pure-unit tests -
 * this file only re-proves the single most important one end-to-end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class EmailTemplateIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void templateCrud_endToEnd() throws Exception {
        String ownerToken = registerOwner("templates-crud-owner");

        MvcResult createResult = mockMvc
                .perform(authed(post("/api/v1/email-templates"), ownerToken)
                        .content("{\"name\":\"Intro\",\"category\":\"SALES\",\"subject\":\"Hi {{contact.firstname}}\","
                                + "\"body\":\"Following up, {{sender.fullname}}\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Intro"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andReturn();
        String templateId = readField(createResult, "data", "id");

        mockMvc.perform(authed(get("/api/v1/email-templates"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(authed(get("/api/v1/email-templates").param("category", "SUPPORT"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(authed(put("/api/v1/email-templates/" + templateId), ownerToken)
                        .content("{\"name\":\"Intro v2\",\"category\":\"SALES\",\"subject\":\"Hi {{contact.firstname}}\","
                                + "\"body\":\"Following up, {{sender.fullname}}\",\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Intro v2"))
                .andExpect(jsonPath("$.data.active").value(false));

        mockMvc.perform(authed(delete("/api/v1/email-templates/" + templateId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/email-templates/" + templateId), ownerToken)).andExpect(status().isNotFound());
    }

    @Test
    void render_mergesRealContactAndAccountFields() throws Exception {
        String ownerToken = registerOwner("templates-render-owner");

        UUID accountId = createAccount(ownerToken, "Acme Rockets");
        String contactId = createContact(ownerToken, "Ada", "Lovelace", accountId);

        MvcResult createResult = mockMvc
                .perform(authed(post("/api/v1/email-templates"), ownerToken)
                        .content("{\"name\":\"Intro\",\"category\":\"SALES\","
                                + "\"subject\":\"Hi {{contact.firstname}} from {{account.name}}\","
                                + "\"body\":\"{{contact.lastname}} - unknown token stays: {{opportunity.name}}\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String templateId = readField(createResult, "data", "id");

        MvcResult renderResult = mockMvc
                .perform(authed(post("/api/v1/email-templates/" + templateId + "/render"), ownerToken)
                        .content("{\"contactId\":\"" + contactId + "\",\"accountId\":\"" + accountId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subject").value("Hi Ada from Acme Rockets"))
                .andReturn();
        JsonNode data = objectMapper.readTree(renderResult.getResponse().getContentAsString()).get("data");
        assertThat(data.get("body").asText()).isEqualTo("Lovelace - unknown token stays: {{opportunity.name}}");
        assertThat(data.get("unresolvedTokens")).hasSize(1);
        assertThat(data.get("unresolvedTokens").get(0).asText()).isEqualTo("{{opportunity.name}}");
    }

    private UUID createAccount(String token, String name) throws Exception {
        MvcResult result = mockMvc
                .perform(authed(post("/api/v1/accounts"), token)
                        .content(objectMapper.writeValueAsString(
                                new CreateAccountRequest(name, null, null, null, null, null, null, null, null, null, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readField(result, "data", "id"));
    }

    private String createContact(String token, String firstName, String lastName, UUID accountId) throws Exception {
        MvcResult result = mockMvc
                .perform(authed(post("/api/v1/contacts"), token)
                        .content(objectMapper.writeValueAsString(new CreateContactRequest(firstName, lastName, null, null, null, null, accountId, null))))
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
