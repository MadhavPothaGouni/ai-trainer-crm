package com.aitrainercrm.platform.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * End-to-end coverage for the Ticket module - the resource that closed the one real gap left in
 * the permission catalog (see V14's migration comment). Covers full CRUD, the free (non-linear)
 * status transition with resolvedAt set/cleared, owner assignment, and CSV import/export - the
 * same shape ImportExportIntegrationTest already covers for Account/Contact/Lead, now proven for
 * the fourth entity too.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class TicketIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void createUpdateListAndDeleteTicket_endToEnd() throws Exception {
        String ownerToken = registerOwner("ticket-crud");
        String accountId = createAccount(ownerToken, "Acme Rockets");
        String contactId = createContact(ownerToken, "Jane", "Doe", accountId);

        MvcResult createResult = mockMvc
                .perform(authed(post("/api/v1/tickets"), ownerToken)
                        .content(
                                """
                                {"subject":"Rocket won't launch","description":"Ignition sequence fails","priority":"HIGH","accountId":"%s","contactId":"%s"}
                                """
                                        .formatted(accountId, contactId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.priority").value("HIGH"))
                .andExpect(jsonPath("$.data.resolvedAt").doesNotExist())
                .andReturn();
        String ticketId = readField(createResult, "data", "id");
        assertThat(ticketId).isNotBlank();

        mockMvc.perform(authed(put("/api/v1/tickets/" + ticketId), ownerToken)
                        .content("{\"subject\":\"Rocket still won't launch\",\"priority\":\"URGENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subject").value("Rocket still won't launch"))
                .andExpect(jsonPath("$.data.priority").value("URGENT"))
                .andExpect(jsonPath("$.data.accountId").doesNotExist());

        mockMvc.perform(authed(get("/api/v1/tickets"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(authed(delete("/api/v1/tickets/" + ticketId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/tickets/" + ticketId), ownerToken)).andExpect(status().isNotFound());
    }

    @Test
    void statusTransitions_setAndClearResolvedAt_freelyInBothDirections() throws Exception {
        String ownerToken = registerOwner("ticket-status");
        String ticketId = createTicket(ownerToken, "Login broken");

        mockMvc.perform(authed(patch("/api/v1/tickets/" + ticketId + "/status"), ownerToken).content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.resolvedAt").doesNotExist());

        mockMvc.perform(authed(patch("/api/v1/tickets/" + ticketId + "/status"), ownerToken).content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"))
                .andExpect(jsonPath("$.data.resolvedAt").exists());

        // Reopening is a normal support workflow, not an invalid transition - see V14's migration
        // comment for why Ticket, unlike Lead/Order, has no one-way state machine.
        mockMvc.perform(authed(patch("/api/v1/tickets/" + ticketId + "/status"), ownerToken).content("{\"status\":\"OPEN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.resolvedAt").doesNotExist());
    }

    @Test
    void assignOwner_movesTheTicketToTheNewOwner() throws Exception {
        String ownerToken = registerOwner("ticket-assign");
        String ticketId = createTicket(ownerToken, "Billing question");

        MvcResult meResult = mockMvc.perform(authed(get("/api/v1/users/me"), ownerToken)).andExpect(status().isOk()).andReturn();
        String ownerId = readField(meResult, "data", "id");
        assertThat(ownerId).isNotBlank();

        mockMvc.perform(authed(patch("/api/v1/tickets/" + ticketId + "/owner"), ownerToken).content("{\"ownerId\":\"" + ownerId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ownerId").value(ownerId));
    }

    @Test
    void importTickets_withValidAndInvalidRows_reportsPerRowResults() throws Exception {
        String ownerToken = registerOwner("ticket-import");
        String csv = "Subject,Priority\nPrinter on fire,URGENT\n,HIGH\nVPN not connecting,NOT_A_PRIORITY\n";

        mockMvc.perform(authedMultipart(csvUpload("/api/v1/tickets/import", "tickets.csv", csv), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.totalRows").value(3))
                .andExpect(jsonPath("$.data.successCount").value(1))
                .andExpect(jsonPath("$.data.errorCount").value(2))
                .andExpect(jsonPath("$.data.errors[1].message").value(containsString("Unknown priority")));

        mockMvc.perform(authed(get("/api/v1/tickets"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void exportTickets_returnsCsvOfVisibleTickets() throws Exception {
        String ownerToken = registerOwner("ticket-export");
        createTicket(ownerToken, "Export me");

        MvcResult exportResult = mockMvc.perform(authed(get("/api/v1/tickets/export"), ownerToken)).andExpect(status().isOk()).andReturn();
        String body = new String(exportResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(body).contains("Export me").contains("Subject,Description,Priority,Account Id,Contact Id");
    }

    private String createAccount(String token, String name) throws Exception {
        MvcResult result = mockMvc
                .perform(authed(post("/api/v1/accounts"), token).content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return readField(result, "data", "id");
    }

    private String createContact(String token, String firstName, String lastName, String accountId) throws Exception {
        MvcResult result = mockMvc
                .perform(authed(post("/api/v1/contacts"), token)
                        .content("{\"firstName\":\"%s\",\"lastName\":\"%s\",\"accountId\":\"%s\"}".formatted(firstName, lastName, accountId)))
                .andExpect(status().isCreated())
                .andReturn();
        return readField(result, "data", "id");
    }

    private String createTicket(String token, String subject) throws Exception {
        MvcResult result = mockMvc
                .perform(authed(post("/api/v1/tickets"), token).content("{\"subject\":\"%s\",\"priority\":\"MEDIUM\"}".formatted(subject)))
                .andExpect(status().isCreated())
                .andReturn();
        return readField(result, "data", "id");
    }

    private MockMultipartHttpServletRequestBuilder csvUpload(String url, String filename, String csvContent) {
        return multipart(url).file(new MockMultipartFile("file", filename, "text/csv", csvContent.getBytes(StandardCharsets.UTF_8)));
    }

    private MockHttpServletRequestBuilder authedMultipart(MockMultipartHttpServletRequestBuilder builder, String accessToken) {
        return builder.header("Authorization", "Bearer " + accessToken);
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
