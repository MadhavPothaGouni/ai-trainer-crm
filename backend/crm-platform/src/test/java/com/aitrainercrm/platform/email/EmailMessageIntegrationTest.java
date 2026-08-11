package com.aitrainercrm.platform.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitrainercrm.platform.auth.dto.RegisterRequest;
import com.aitrainercrm.platform.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
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
 * End-to-end coverage for the EmailMessage module - see V15's migration comment for why this is
 * genuinely new scope (no permission-catalog gap to close, unlike Ticket). Covers full CRUD, the
 * relatedTo validation borrowed from ActivityService, owner assignment, and CSV export.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class EmailMessageIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void createUpdateListAndDeleteEmail_endToEnd() throws Exception {
        String ownerToken = registerOwner("email-crud");
        String accountId = createAccount(ownerToken, "Acme Rockets");

        MvcResult createResult = mockMvc
                .perform(authed(post("/api/v1/email-messages"), ownerToken)
                        .content(
                                """
                                {"direction":"OUTBOUND","subject":"Following up","fromAddress":"rep@example.com",
                                 "toAddresses":"buyer@example.com","relatedToType":"ACCOUNT","relatedToId":"%s"}
                                """
                                        .formatted(accountId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.direction").value("OUTBOUND"))
                .andExpect(jsonPath("$.data.subject").value("Following up"))
                .andExpect(jsonPath("$.data.sentAt").exists())
                .andReturn();
        String emailId = readField(createResult, "data", "id");
        assertThat(emailId).isNotBlank();

        mockMvc.perform(authed(put("/api/v1/email-messages/" + emailId), ownerToken)
                        .content(
                                """
                                {"direction":"OUTBOUND","subject":"Following up again","fromAddress":"rep@example.com",
                                 "toAddresses":"buyer@example.com","relatedToType":"ACCOUNT","relatedToId":"%s"}
                                """
                                        .formatted(accountId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subject").value("Following up again"));

        mockMvc.perform(authed(get("/api/v1/email-messages"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(authed(get("/api/v1/email-messages")
                        .param("relatedToType", "ACCOUNT")
                        .param("relatedToId", accountId), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(authed(delete("/api/v1/email-messages/" + emailId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/email-messages/" + emailId), ownerToken)).andExpect(status().isNotFound());
    }

    @Test
    void createEmail_withUnknownRelatedRecord_returns404() throws Exception {
        String ownerToken = registerOwner("email-badrelated");

        mockMvc.perform(authed(post("/api/v1/email-messages"), ownerToken)
                        .content(
                                """
                                {"direction":"INBOUND","subject":"Hi","fromAddress":"a@example.com",
                                 "toAddresses":"b@example.com","relatedToType":"ACCOUNT","relatedToId":"%s"}
                                """
                                        .formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void assignOwner_movesTheEmailToTheNewOwner() throws Exception {
        String ownerToken = registerOwner("email-assign");
        String accountId = createAccount(ownerToken, "Acme Rockets");
        String emailId = createEmail(ownerToken, accountId);

        MvcResult meResult = mockMvc.perform(authed(get("/api/v1/users/me"), ownerToken)).andExpect(status().isOk()).andReturn();
        String ownerId = readField(meResult, "data", "id");

        mockMvc.perform(authed(patch("/api/v1/email-messages/" + emailId + "/owner"), ownerToken).content("{\"ownerId\":\"" + ownerId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ownerId").value(ownerId));
    }

    @Test
    void exportEmails_returnsCsvOfVisibleEmails() throws Exception {
        String ownerToken = registerOwner("email-export");
        String accountId = createAccount(ownerToken, "Acme Rockets");
        createEmail(ownerToken, accountId);

        MvcResult exportResult =
                mockMvc.perform(authed(get("/api/v1/email-messages/export"), ownerToken)).andExpect(status().isOk()).andReturn();
        String body = new String(exportResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(body).contains("buyer@example.com").contains("Direction,Subject,From,To,Cc,Related To Type,Related To Id,Sent At,Created At");
    }

    private String createAccount(String token, String name) throws Exception {
        MvcResult result = mockMvc
                .perform(authed(post("/api/v1/accounts"), token).content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return readField(result, "data", "id");
    }

    private String createEmail(String token, String accountId) throws Exception {
        MvcResult result = mockMvc
                .perform(authed(post("/api/v1/email-messages"), token)
                        .content(
                                """
                                {"direction":"OUTBOUND","subject":"Following up","fromAddress":"rep@example.com",
                                 "toAddresses":"buyer@example.com","relatedToType":"ACCOUNT","relatedToId":"%s"}
                                """
                                        .formatted(accountId)))
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
