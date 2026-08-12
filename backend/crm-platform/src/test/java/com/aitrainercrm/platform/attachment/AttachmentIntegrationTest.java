package com.aitrainercrm.platform.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitrainercrm.platform.account.dto.CreateAccountRequest;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * End-to-end coverage for the Attachment module - upload/list/download/update/delete/
 * assign-owner, plus the two checks that matter most for a file-upload endpoint specifically:
 * the bytes that come back out of GET /{id}/download are byte-for-byte what was uploaded, and
 * an unrelated-record reference is rejected the same way EmailMessage/CalendarEvent's is.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AttachmentIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void uploadListDownloadUpdateAndDelete_endToEnd() throws Exception {
        String ownerToken = registerOwner("attach-crud");
        String accountId = createAccount(ownerToken, "Initech Corp");

        byte[] fileContent = "%PDF-1.4 fake contract bytes".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "contract.pdf", "application/pdf", fileContent);

        MockMultipartHttpServletRequestBuilder uploadRequest = multipart("/api/v1/attachments")
                .file(file)
                .param("relatedToType", "ACCOUNT")
                .param("relatedToId", accountId)
                .param("description", "Signed contract");
        MvcResult uploadResult = mockMvc
                .perform(authedMultipart(uploadRequest, ownerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.fileName").value("contract.pdf"))
                .andExpect(jsonPath("$.data.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.data.fileSizeBytes").value(fileContent.length))
                .andExpect(jsonPath("$.data.description").value("Signed contract"))
                .andReturn();
        String attachmentId = readField(uploadResult, "data", "id");

        // Metadata never leaks the storage key.
        mockMvc.perform(authed(get("/api/v1/attachments/" + attachmentId), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.storageKey").doesNotExist());

        mockMvc.perform(authed(get("/api/v1/attachments")
                        .param("relatedToType", "ACCOUNT")
                        .param("relatedToId", accountId), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        // The downloaded bytes match exactly what was uploaded.
        MvcResult downloadResult = mockMvc
                .perform(authed(get("/api/v1/attachments/" + attachmentId + "/download"), ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(downloadResult.getResponse().getContentAsByteArray()).isEqualTo(fileContent);
        assertThat(downloadResult.getResponse().getHeader("Content-Disposition")).contains("contract.pdf");

        mockMvc.perform(authed(put("/api/v1/attachments/" + attachmentId), ownerToken)
                        .content("{\"fileName\":\"contract-signed.pdf\",\"description\":\"Countersigned\","
                                + "\"relatedToType\":\"ACCOUNT\",\"relatedToId\":\"" + accountId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileName").value("contract-signed.pdf"))
                .andExpect(jsonPath("$.data.description").value("Countersigned"));

        mockMvc.perform(authed(delete("/api/v1/attachments/" + attachmentId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/attachments/" + attachmentId), ownerToken)).andExpect(status().isNotFound());
    }

    @Test
    void uploadWithUnknownRelatedAccount_returns404() throws Exception {
        String ownerToken = registerOwner("attach-badrelated");
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));

        MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/attachments")
                .file(file)
                .param("relatedToType", "ACCOUNT")
                .param("relatedToId", UUID.randomUUID().toString());
        mockMvc.perform(authedMultipart(request, ownerToken)).andExpect(status().isNotFound());
    }

    @Test
    void uploadWithEmptyFile_returns400() throws Exception {
        String ownerToken = registerOwner("attach-empty");
        String accountId = createAccount(ownerToken, "Empty Co");
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/attachments")
                .file(emptyFile)
                .param("relatedToType", "ACCOUNT")
                .param("relatedToId", accountId);
        mockMvc.perform(authedMultipart(request, ownerToken)).andExpect(status().isBadRequest());
    }

    @Test
    void assignOwner_reassignsTheAttachment() throws Exception {
        String ownerToken = registerOwner("attach-assign");
        String ownerId = readField(mockMvc.perform(authed(get("/api/v1/users/me"), ownerToken)).andExpect(status().isOk()).andReturn(), "data", "id");
        String accountId = createAccount(ownerToken, "Reassign Co");
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", "fake-png-bytes".getBytes(StandardCharsets.UTF_8));

        MockMultipartHttpServletRequestBuilder uploadRequest = multipart("/api/v1/attachments")
                .file(file)
                .param("relatedToType", "ACCOUNT")
                .param("relatedToId", accountId);
        String attachmentId = readField(
                mockMvc.perform(authedMultipart(uploadRequest, ownerToken)).andExpect(status().isCreated()).andReturn(),
                "data", "id");

        // OWNER is the only real user in this org - reassigning to themselves is a no-op but
        // still proves the endpoint round-trips correctly end to end.
        mockMvc.perform(authed(patch("/api/v1/attachments/" + attachmentId + "/owner"), ownerToken)
                        .content("{\"ownerId\":\"" + ownerId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(attachmentId));
    }

    private String createAccount(String ownerToken, String name) throws Exception {
        CreateAccountRequest createAccount = new CreateAccountRequest(name, null, null, null, null, null, null, null, null, null, null, null, null);
        MvcResult createResult = mockMvc
                .perform(authed(post("/api/v1/accounts"), ownerToken).content(objectMapper.writeValueAsString(createAccount)))
                .andExpect(status().isCreated())
                .andReturn();
        return readField(createResult, "data", "id");
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

    /** No .contentType() call, unlike authed() - the multipart builder already sets its own multipart/form-data content type, and overriding it here would break the upload. */
    private MockHttpServletRequestBuilder authedMultipart(MockMultipartHttpServletRequestBuilder builder, String accessToken) {
        return builder.header("Authorization", "Bearer " + accessToken);
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
