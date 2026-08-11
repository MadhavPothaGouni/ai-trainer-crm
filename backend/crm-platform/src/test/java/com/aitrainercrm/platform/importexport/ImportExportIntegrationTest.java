package com.aitrainercrm.platform.importexport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * End-to-end coverage for bulk CSV import/export - see {@code ImportExportService}'s javadoc for
 * why this module exists. Deliberately exercises the partial-success path (some rows succeed,
 * some fail, the job reports both) rather than only the all-succeed happy path, since that's the
 * entire point of a per-row error report; a suite that only ever uploaded perfectly clean CSVs
 * wouldn't actually prove the feature works.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ImportExportIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void importAccounts_withValidAndInvalidRows_reportsPerRowResultsAndCreatesOnlyTheValidOnes() throws Exception {
        String ownerToken = registerOwner("import-accounts");
        String csv = "Name,Industry,Website\nAcme Rockets,Aerospace,acme.example.com\n,Bad Co,nowhere.example.com\nWidgets Inc,,\n";

        MvcResult importResult = mockMvc
                .perform(authedMultipart(csvUpload("/api/v1/accounts/import", "accounts.csv", csv), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.totalRows").value(3))
                .andExpect(jsonPath("$.data.successCount").value(2))
                .andExpect(jsonPath("$.data.errorCount").value(1))
                .andExpect(jsonPath("$.data.errors[0].rowNumber").value(2))
                .andExpect(jsonPath("$.data.errors[0].message").value("Name is required"))
                .andReturn();
        String jobId = readField(importResult, "data", "id");
        assertThat(jobId).isNotBlank();

        mockMvc.perform(authed(get("/api/v1/accounts"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void exportAccounts_returnsCsvOfVisibleAccountsWithProperQuoting() throws Exception {
        String ownerToken = registerOwner("export-accounts");
        mockMvc.perform(authed(post("/api/v1/accounts"), ownerToken)
                        .content("{\"name\":\"Comma, Inc\",\"description\":\"Sells widgets, gadgets, and gizmos\"}"))
                .andExpect(status().isCreated());

        MvcResult exportResult = mockMvc
                .perform(authed(get("/api/v1/accounts/export"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("accounts.csv")))
                .andReturn();

        String body = new String(exportResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(body).contains("\"Comma, Inc\"").contains("\"Sells widgets, gadgets, and gizmos\"");
    }

    @Test
    void importLeads_withUnknownSource_recordsRowError() throws Exception {
        String ownerToken = registerOwner("import-leads-bad-source");
        String csv = "First Name,Last Name,Source\nJane,Doe,NOT_A_REAL_SOURCE\n";

        mockMvc.perform(authedMultipart(csvUpload("/api/v1/leads/import", "leads.csv", csv), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successCount").value(0))
                .andExpect(jsonPath("$.data.errorCount").value(1))
                .andExpect(jsonPath("$.data.errors[0].message").value(containsString("Unknown source")));
    }

    @Test
    void importContacts_missingRequiredColumn_returnsFailedJobBeforeProcessingAnyRow() throws Exception {
        String ownerToken = registerOwner("import-contacts-bad-header");
        String csv = "First Name,Email\nJane,jane@example.com\n";

        mockMvc.perform(authedMultipart(csvUpload("/api/v1/contacts/import", "contacts.csv", csv), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.errors[0].message").value(containsString("Last Name")));

        mockMvc.perform(authed(get("/api/v1/contacts"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void exportThenReimportLeads_roundTripsCleanlyThroughCsvWriterAndCsvParser() throws Exception {
        String ownerToken = registerOwner("leads-round-trip");
        mockMvc.perform(authed(post("/api/v1/leads"), ownerToken)
                        .content("{\"firstName\":\"Ada\",\"lastName\":\"Lovelace\",\"source\":\"REFERRAL\",\"description\":\"Met at the, conference\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(authed(post("/api/v1/leads"), ownerToken)
                        .content("{\"firstName\":\"Grace\",\"lastName\":\"Hopper\",\"source\":\"EVENT\"}"))
                .andExpect(status().isCreated());

        MvcResult exportResult = mockMvc.perform(authed(get("/api/v1/leads/export"), ownerToken)).andExpect(status().isOk()).andReturn();
        byte[] exportedCsv = exportResult.getResponse().getContentAsByteArray();

        mockMvc.perform(authedMultipart(
                        multipart("/api/v1/leads/import").file(new MockMultipartFile("file", "leads.csv", "text/csv", exportedCsv)), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.totalRows").value(2))
                .andExpect(jsonPath("$.data.errorCount").value(0))
                .andExpect(jsonPath("$.data.successCount").value(2));

        // The BOM-prefixed, comma-containing, re-imported file created two more leads on top of the
        // two exported ones - four total proves both the export and the re-import actually ran.
        mockMvc.perform(authed(get("/api/v1/leads"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(4));
    }

    @Test
    void importJobHistory_listsAndReturnsJobWithItsErrors() throws Exception {
        String ownerToken = registerOwner("import-job-history");
        // A lone space, not an empty line - CsvParser treats a truly empty line as a blank line to
        // skip (see its javadoc), so this is deliberately " " to produce one real, but blank-named, row.
        String csv = "Name\n \nValid Co\n";

        MvcResult importResult = mockMvc
                .perform(authedMultipart(csvUpload("/api/v1/accounts/import", "accounts.csv", csv), ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        UUID jobId = UUID.fromString(readField(importResult, "data", "id"));

        mockMvc.perform(authed(get("/api/v1/import-jobs"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].entityType").value("ACCOUNT"));

        mockMvc.perform(authed(get("/api/v1/import-jobs/" + jobId), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.errors.length()").value(1))
                .andExpect(jsonPath("$.data.errors[0].rowNumber").value(1));
    }

    private MockMultipartHttpServletRequestBuilder csvUpload(String url, String filename, String csvContent) {
        return multipart(url).file(new MockMultipartFile("file", filename, "text/csv", csvContent.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Multipart requests must not get {@code Content-Type: application/json} forced onto them the
     * way {@link #authed} does for every other request in this suite - {@code multipart(...)}
     * already sets {@code multipart/form-data} with the correct boundary, and overriding that would
     * make the request unparseable. This only adds the auth header.
     */
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
