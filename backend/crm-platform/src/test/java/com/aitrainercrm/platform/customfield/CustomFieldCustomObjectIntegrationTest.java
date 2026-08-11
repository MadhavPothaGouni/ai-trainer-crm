package com.aitrainercrm.platform.customfield;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitrainercrm.platform.auth.dto.RegisterRequest;
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
 * End-to-end coverage for platform extensibility: Custom Objects (a generic
 * "Name" + admin-defined fields entity) and Custom Fields attached either to
 * a Custom Object or to a standard CRM entity (ACCOUNT here). Exercises the
 * exactly-one-of-target validation (mirroring CampaignMember's lead/contact
 * check in V9), the per-{@code FieldType} value parsing/validation in
 * {@code CustomFieldService#parseAndValidate}, picklist-shape validation,
 * required-field enforcement, and duplicate-apiName rejection - real
 * business logic a request/response round trip catches far more
 * convincingly than a unit test mocking the repositories.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CustomFieldCustomObjectIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void customObjectWithFields_recordLifecycleAndValueValidation_endToEnd() throws Exception {
        String ownerToken = registerOwner("custobj-owner");

        // --- Create a custom object ---
        String createObjectBody = """
                {"apiName":"project","label":"Project","pluralLabel":"Projects","description":"Internal delivery projects"}""";
        MvcResult objectResult = mockMvc
                .perform(authed(post("/api/v1/custom-objects"), ownerToken).content(createObjectBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.apiName").value("project"))
                .andReturn();
        UUID customObjectId = UUID.fromString(readField(objectResult, "data", "id"));

        // --- Duplicate apiName within the same org is rejected ---
        mockMvc.perform(authed(post("/api/v1/custom-objects"), ownerToken).content(createObjectBody))
                .andExpect(status().isConflict());

        // --- Attach a NUMBER field and a PICKLIST field to the custom object ---
        String budgetFieldBody = """
                {"customObjectId":"%s","apiName":"budget","label":"Budget","fieldType":"NUMBER","required":true}"""
                .formatted(customObjectId);
        MvcResult budgetFieldResult = mockMvc
                .perform(authed(post("/api/v1/custom-fields"), ownerToken).content(budgetFieldBody))
                .andExpect(status().isCreated())
                .andReturn();
        UUID budgetFieldId = UUID.fromString(readField(budgetFieldResult, "data", "id"));

        String priorityFieldBody = """
                {"customObjectId":"%s","apiName":"priority","label":"Priority","fieldType":"PICKLIST","picklistValues":["LOW","MEDIUM","HIGH"]}"""
                .formatted(customObjectId);
        MvcResult priorityFieldResult = mockMvc
                .perform(authed(post("/api/v1/custom-fields"), ownerToken).content(priorityFieldBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.picklistValues.length()").value(3))
                .andReturn();
        UUID priorityFieldId = UUID.fromString(readField(priorityFieldResult, "data", "id"));

        // --- A field naming both a target and no target is rejected ---
        mockMvc.perform(authed(post("/api/v1/custom-fields"), ownerToken)
                        .content("{\"apiName\":\"x\",\"label\":\"X\",\"fieldType\":\"TEXT\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(authed(post("/api/v1/custom-fields"), ownerToken)
                        .content("{\"standardEntityType\":\"ACCOUNT\",\"customObjectId\":\"%s\",\"apiName\":\"x\",\"label\":\"X\",\"fieldType\":\"TEXT\"}"
                                .formatted(customObjectId)))
                .andExpect(status().isBadRequest());

        // --- A non-PICKLIST field with picklist values is rejected, and vice versa ---
        mockMvc.perform(authed(post("/api/v1/custom-fields"), ownerToken)
                        .content("{\"customObjectId\":\"%s\",\"apiName\":\"bad1\",\"label\":\"Bad\",\"fieldType\":\"TEXT\",\"picklistValues\":[\"A\"]}"
                                .formatted(customObjectId)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(authed(post("/api/v1/custom-fields"), ownerToken)
                        .content("{\"customObjectId\":\"%s\",\"apiName\":\"bad2\",\"label\":\"Bad\",\"fieldType\":\"PICKLIST\"}"
                                .formatted(customObjectId)))
                .andExpect(status().isBadRequest());

        // --- Duplicate apiName on the same target is rejected ---
        mockMvc.perform(authed(post("/api/v1/custom-fields"), ownerToken).content(budgetFieldBody))
                .andExpect(status().isConflict());

        // --- Create a record under the custom object ---
        MvcResult recordResult = mockMvc
                .perform(authed(post("/api/v1/custom-objects/" + customObjectId + "/records"), ownerToken)
                        .content("{\"name\":\"Mars Rover Ops\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID recordId = UUID.fromString(readField(recordResult, "data", "id"));

        // --- Setting an invalid number is rejected ---
        mockMvc.perform(authed(put("/api/v1/custom-fields/values")
                                .param("customObjectId", customObjectId.toString())
                                .param("recordId", recordId.toString()),
                        ownerToken)
                        .content("{\"values\":{\"" + budgetFieldId + "\":\"not-a-number\"}}"))
                .andExpect(status().isBadRequest());

        // --- Setting an invalid picklist value is rejected ---
        mockMvc.perform(authed(put("/api/v1/custom-fields/values")
                                .param("customObjectId", customObjectId.toString())
                                .param("recordId", recordId.toString()),
                        ownerToken)
                        .content("{\"values\":{\"" + priorityFieldId + "\":\"URGENT\"}}"))
                .andExpect(status().isBadRequest());

        // --- Clearing the required budget field is rejected ---
        mockMvc.perform(authed(put("/api/v1/custom-fields/values")
                                .param("customObjectId", customObjectId.toString())
                                .param("recordId", recordId.toString()),
                        ownerToken)
                        .content("{\"values\":{\"" + budgetFieldId + "\":\"\"}}"))
                .andExpect(status().isBadRequest());

        // --- Valid values save and round-trip correctly ---
        mockMvc.perform(authed(put("/api/v1/custom-fields/values")
                                .param("customObjectId", customObjectId.toString())
                                .param("recordId", recordId.toString()),
                        ownerToken)
                        .content("{\"values\":{\"" + budgetFieldId + "\":\"12500.5\",\"" + priorityFieldId + "\":\"HIGH\"}}"))
                .andExpect(status().isOk());

        mockMvc.perform(authed(get("/api/v1/custom-fields/values")
                        .param("customObjectId", customObjectId.toString())
                        .param("recordId", recordId.toString()), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        // --- Deleting the custom object cascades away its records and fields (a follow-up get 404s) ---
        mockMvc.perform(authed(delete("/api/v1/custom-objects/" + customObjectId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/custom-objects/" + customObjectId), ownerToken)).andExpect(status().isNotFound());
    }

    @Test
    void customFieldOnStandardEntity_setAndGetValue_endToEnd() throws Exception {
        String ownerToken = registerOwner("custfield-owner");

        MvcResult accountResult = mockMvc
                .perform(authed(post("/api/v1/accounts"), ownerToken).content("{\"name\":\"Acme Rockets\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID accountId = UUID.fromString(readField(accountResult, "data", "id"));

        MvcResult fieldResult = mockMvc
                .perform(authed(post("/api/v1/custom-fields"), ownerToken)
                        .content("{\"standardEntityType\":\"ACCOUNT\",\"apiName\":\"launch_site\",\"label\":\"Launch Site\",\"fieldType\":\"TEXT\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID fieldId = UUID.fromString(readField(fieldResult, "data", "id"));

        mockMvc.perform(authed(get("/api/v1/custom-fields").param("standardEntityType", "ACCOUNT"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(authed(put("/api/v1/custom-fields/values")
                                .param("standardEntityType", "ACCOUNT")
                                .param("recordId", accountId.toString()),
                        ownerToken)
                        .content("{\"values\":{\"" + fieldId + "\":\"Cape Canaveral\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].value").value("Cape Canaveral"));

        MvcResult getResult = mockMvc
                .perform(authed(get("/api/v1/custom-fields/values")
                        .param("standardEntityType", "ACCOUNT")
                        .param("recordId", accountId.toString()), ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(getResult.getResponse().getContentAsString()).contains("Cape Canaveral").contains("launch_site");

        // --- Neither standardEntityType nor customObjectId given is rejected ---
        mockMvc.perform(authed(get("/api/v1/custom-fields/values").param("recordId", accountId.toString()), ownerToken))
                .andExpect(status().isBadRequest());
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
