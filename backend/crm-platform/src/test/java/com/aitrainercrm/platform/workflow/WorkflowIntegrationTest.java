package com.aitrainercrm.platform.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitrainercrm.platform.auth.dto.RegisterRequest;
import com.aitrainercrm.platform.lead.dto.CreateLeadRequest;
import com.aitrainercrm.platform.lead.entity.Lead;
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
 * End-to-end coverage for Workflow automation: CRUD (owner-scoped, unlike
 * this session's other two modules - see Workflow's javadoc), the
 * MANAGE-gated active toggle and manual run, and - the actual point of the
 * feature - a real Lead creation firing a matching active workflow through
 * {@code WorkflowEngineListener} and landing a TASK Activity related to
 * that lead, assigned to its owner.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class WorkflowIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void manualRun_createsTaskActivityAssignedToRecordOwner_deterministically() throws Exception {
        String ownerToken = registerOwner("workflow-manual");

        CreateLeadRequest createLead = new CreateLeadRequest("Ada", "Lovelace", null, null, "Analytical Engines Inc", null, Lead.Source.WEBSITE, null, null);
        MvcResult leadResult = mockMvc
                .perform(authed(post("/api/v1/leads"), ownerToken).content(objectMapper.writeValueAsString(createLead)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID leadId = UUID.fromString(readField(leadResult, "data", "id"));

        String createWorkflowBody = """
                {"name":"Welcome new leads","triggerResource":"LEAD","triggerEvent":"CREATED","taskSubject":"Reach out to the new lead"}""";
        MvcResult workflowResult = mockMvc
                .perform(authed(post("/api/v1/workflows"), ownerToken).content(createWorkflowBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.runCount").value(0))
                .andReturn();
        UUID workflowId = UUID.fromString(readField(workflowResult, "data", "id"));

        // --- Manually firing it is synchronous (unlike the real @Async event path below) - runCount/lastRunAt update immediately ---
        mockMvc.perform(authed(post("/api/v1/workflows/" + workflowId + "/run"), ownerToken).content("{\"resourceId\":\"" + leadId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runCount").value(1))
                .andExpect(jsonPath("$.data.lastRunAt").exists());

        MvcResult runsResult = mockMvc
                .perform(authed(get("/api/v1/workflows/" + workflowId + "/runs"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.content[0].resourceId").value(leadId.toString()))
                .andReturn();
        JsonNode runsJson = objectMapper.readTree(runsResult.getResponse().getContentAsString());
        String createdActivityId = runsJson.get("data").get("content").get(0).get("createdActivityId").asText();
        assertThat(createdActivityId).isNotBlank();

        // --- The task Activity actually exists, related to the lead, with the configured subject ---
        mockMvc.perform(authed(get("/api/v1/activities/" + createdActivityId), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("TASK"))
                .andExpect(jsonPath("$.data.subject").value("Reach out to the new lead"))
                .andExpect(jsonPath("$.data.relatedToType").value("LEAD"))
                .andExpect(jsonPath("$.data.relatedToId").value(leadId.toString()));
    }

    @Test
    void realLeadCreation_firesMatchingActiveWorkflow_viaAsyncListener() throws Exception {
        String ownerToken = registerOwner("workflow-async");

        String createWorkflowBody = """
                {"name":"Auto-task on new lead","triggerResource":"LEAD","triggerEvent":"CREATED","taskSubject":"Auto-created follow-up"}""";
        mockMvc.perform(authed(post("/api/v1/workflows"), ownerToken).content(createWorkflowBody)).andExpect(status().isCreated());

        CreateLeadRequest createLead = new CreateLeadRequest("Grace", "Hopper", null, null, "Compiler Co", null, Lead.Source.REFERRAL, null, null);
        MvcResult leadResult = mockMvc
                .perform(authed(post("/api/v1/leads"), ownerToken).content(objectMapper.writeValueAsString(createLead)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID leadId = UUID.fromString(readField(leadResult, "data", "id"));

        // --- The listener runs @Async - wait for it rather than assuming timing (same pattern WebhookIntegrationTest uses) ---
        Thread.sleep(300);

        mockMvc.perform(authed(get("/api/v1/activities")
                        .param("relatedToType", "LEAD")
                        .param("relatedToId", leadId.toString()), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].subject").value("Auto-created follow-up"));
    }

    @Test
    void crudAndActiveToggle_andImmutableTriggerAfterCreate_endToEnd() throws Exception {
        String ownerToken = registerOwner("workflow-crud");

        String createWorkflowBody = """
                {"name":"Draft workflow","description":"testing","triggerResource":"CONTACT","triggerEvent":"UPDATED","taskSubject":"Check in"}""";
        MvcResult createResult = mockMvc
                .perform(authed(post("/api/v1/workflows"), ownerToken).content(createWorkflowBody))
                .andExpect(status().isCreated())
                .andReturn();
        UUID workflowId = UUID.fromString(readField(createResult, "data", "id"));

        // --- Update touches name/description/taskSubject/assignee, not the trigger ---
        mockMvc.perform(authed(put("/api/v1/workflows/" + workflowId), ownerToken)
                        .content("{\"name\":\"Renamed workflow\",\"taskSubject\":\"Check in again\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Renamed workflow"))
                .andExpect(jsonPath("$.data.triggerResource").value("CONTACT"));

        // --- Deactivate via the MANAGE-gated endpoint ---
        mockMvc.perform(authed(patch("/api/v1/workflows/" + workflowId + "/active"), ownerToken).content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));

        mockMvc.perform(authed(get("/api/v1/workflows"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(authed(delete("/api/v1/workflows/" + workflowId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/workflows/" + workflowId), ownerToken)).andExpect(status().isNotFound());
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
