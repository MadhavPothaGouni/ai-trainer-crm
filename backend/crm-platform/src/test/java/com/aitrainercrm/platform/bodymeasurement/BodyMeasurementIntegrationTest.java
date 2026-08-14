package com.aitrainercrm.platform.bodymeasurement;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * End-to-end coverage for the Body Measurement module - see V41's migration comment and
 * BodyMeasurement's javadoc for the gap this fills (a time series of periodic check-ins,
 * distinct from ClientGoal's single mutable objective row). Covers full CRUD; no status
 * transition coverage since this module has no status field.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class BodyMeasurementIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void createUpdateListAndDeleteBodyMeasurement_endToEnd() throws Exception {
        String ownerToken = registerOwner("measurement-crud");
        String contactId = createContact(ownerToken, "Jamie", "Client");

        MvcResult createResult = mockMvc
                .perform(authed(post("/api/v1/body-measurements"), ownerToken)
                        .content(
                                """
                                {"contactId":"%s","measuredAt":"2027-01-15","weightValue":182.50,"weightUnit":"lbs",
                                "bodyFatPercent":18.20,"chestCm":101.00,"waistCm":88.50,"hipsCm":102.00,"notes":"Kickoff"}
                                """
                                        .formatted(contactId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.measuredAt").value("2027-01-15"))
                .andExpect(jsonPath("$.data.weightValue").value(182.50))
                .andReturn();
        String measurementId = readField(createResult, "data", "id");
        assertThat(measurementId).isNotBlank();

        mockMvc.perform(authed(put("/api/v1/body-measurements/" + measurementId), ownerToken)
                        .content(
                                """
                                {"measuredAt":"2027-01-22","weightValue":180.00,"weightUnit":"lbs",
                                "bodyFatPercent":17.50,"chestCm":100.00,"waistCm":87.00,"hipsCm":101.00,"notes":"Week 2"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weightValue").value(180.00))
                .andExpect(jsonPath("$.data.notes").value("Week 2"));

        mockMvc.perform(authed(get("/api/v1/body-measurements"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(authed(delete("/api/v1/body-measurements/" + measurementId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/body-measurements/" + measurementId), ownerToken)).andExpect(status().isNotFound());
    }

    @Test
    void createBodyMeasurement_withMissingRequiredFields_isRejected() throws Exception {
        String ownerToken = registerOwner("measurement-baddata");

        mockMvc.perform(authed(post("/api/v1/body-measurements"), ownerToken).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createBodyMeasurement_withContactFromAnotherOrganization_isRejected() throws Exception {
        String ownerToken = registerOwner("measurement-crossorg");
        String otherOrgToken = registerOwner("measurement-otherorg");
        String otherOrgContactId = createContact(otherOrgToken, "Other", "Client");

        mockMvc.perform(authed(post("/api/v1/body-measurements"), ownerToken)
                        .content("{\"contactId\":\"%s\",\"measuredAt\":\"2027-01-15\"}".formatted(otherOrgContactId)))
                .andExpect(status().isNotFound());
    }

    private String createContact(String token, String firstName, String lastName) throws Exception {
        MvcResult result = mockMvc
                .perform(authed(post("/api/v1/contacts"), token)
                        .content("{\"firstName\":\"%s\",\"lastName\":\"%s\"}".formatted(firstName, lastName)))
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
