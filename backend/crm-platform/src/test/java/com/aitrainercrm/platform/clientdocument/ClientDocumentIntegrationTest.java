package com.aitrainercrm.platform.clientdocument;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * End-to-end coverage for the Client Document module - see V48's migration comment and
 * ClientDocument's javadoc for the gap this fills. Covers full CRUD and the free status
 * transition with signedAt stamped once and never overwritten, the same shape
 * ClientGoalIntegrationTest already covers for achievedAt.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ClientDocumentIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void createUpdateListAndDeleteClientDocument_endToEnd() throws Exception {
        String ownerToken = registerOwner("document-crud");
        String contactId = createContact(ownerToken, "Jamie", "Client");

        MvcResult createResult = mockMvc
                .perform(authed(post("/api/v1/client-documents"), ownerToken)
                        .content(
                                """
                                {"contactId":"%s","documentType":"WAIVER","title":"Liability Waiver","expiresAt":"2027-01-01"}
                                """
                                        .formatted(contactId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.documentType").value("WAIVER"))
                .andExpect(jsonPath("$.data.signedAt").doesNotExist())
                .andReturn();
        String documentId = readField(createResult, "data", "id");
        assertThat(documentId).isNotBlank();

        mockMvc.perform(authed(put("/api/v1/client-documents/" + documentId), ownerToken)
                        .content("{\"documentType\":\"WAIVER\",\"title\":\"Liability Waiver (revised)\",\"expiresAt\":\"2027-06-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Liability Waiver (revised)"));

        mockMvc.perform(authed(get("/api/v1/client-documents"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(authed(delete("/api/v1/client-documents/" + documentId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/client-documents/" + documentId), ownerToken)).andExpect(status().isNotFound());
    }

    @Test
    void statusTransitionToSigned_stampsSignedAtOnlyOnce() throws Exception {
        String ownerToken = registerOwner("document-status");
        String contactId = createContact(ownerToken, "Riley", "Client");
        String documentId = createDocument(ownerToken, contactId, "Medical Clearance");

        mockMvc.perform(authed(patch("/api/v1/client-documents/" + documentId + "/status"), ownerToken).content("{\"status\":\"SIGNED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SIGNED"))
                .andExpect(jsonPath("$.data.signedAt").exists());

        MvcResult afterFirstSign = mockMvc.perform(authed(get("/api/v1/client-documents/" + documentId), ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        String signedAtAfterFirst = readField(afterFirstSign, "data", "signedAt");

        // Moving away and back to SIGNED - a legitimate correction, same restraint
        // contracts.status/client_goals.status take - must not overwrite signedAt.
        mockMvc.perform(authed(patch("/api/v1/client-documents/" + documentId + "/status"), ownerToken).content("{\"status\":\"REVOKED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(authed(patch("/api/v1/client-documents/" + documentId + "/status"), ownerToken).content("{\"status\":\"SIGNED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.signedAt").value(signedAtAfterFirst));
    }

    private String createContact(String token, String firstName, String lastName) throws Exception {
        MvcResult result = mockMvc
                .perform(authed(post("/api/v1/contacts"), token)
                        .content("{\"firstName\":\"%s\",\"lastName\":\"%s\"}".formatted(firstName, lastName)))
                .andExpect(status().isCreated())
                .andReturn();
        return readField(result, "data", "id");
    }

    private String createDocument(String token, String contactId, String title) throws Exception {
        MvcResult result = mockMvc
                .perform(authed(post("/api/v1/client-documents"), token)
                        .content("{\"contactId\":\"%s\",\"documentType\":\"MEDICAL_CLEARANCE\",\"title\":\"%s\"}".formatted(contactId, title)))
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
