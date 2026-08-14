package com.aitrainercrm.platform.nutritionplan;

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
 * End-to-end coverage for the Nutrition Plan module - see V40's migration comment and
 * NutritionPlan's javadoc for the gap this fills (ClientGoal tracks a long-term outcome,
 * TrainingSession/TrainingSessionExercise log workouts, Exercise catalogs movements - nothing
 * else covers dietary/macro guidance). Covers full CRUD, the free (non-linear) status
 * transition, and the start/end date ordering validation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class NutritionPlanIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void createUpdateListAndDeleteNutritionPlan_endToEnd() throws Exception {
        String ownerToken = registerOwner("plan-crud");
        String contactId = createContact(ownerToken, "Jamie", "Client");

        MvcResult createResult = mockMvc
                .perform(authed(post("/api/v1/nutrition-plans"), ownerToken)
                        .content(
                                """
                                {"contactId":"%s","title":"Cutting phase","dailyCalorieTarget":2200,
                                "proteinTargetGrams":180,"carbTargetGrams":200,"fatTargetGrams":60,
                                "startDate":"2027-01-01","endDate":"2027-03-01"}
                                """
                                        .formatted(contactId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.dailyCalorieTarget").value(2200))
                .andReturn();
        String planId = readField(createResult, "data", "id");
        assertThat(planId).isNotBlank();

        mockMvc.perform(authed(put("/api/v1/nutrition-plans/" + planId), ownerToken)
                        .content(
                                """
                                {"title":"Cutting phase (revised)","dailyCalorieTarget":2100,
                                "proteinTargetGrams":190,"carbTargetGrams":180,"fatTargetGrams":55,
                                "startDate":"2027-01-01","endDate":"2027-03-15"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Cutting phase (revised)"))
                .andExpect(jsonPath("$.data.dailyCalorieTarget").value(2100));

        mockMvc.perform(authed(get("/api/v1/nutrition-plans"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(authed(delete("/api/v1/nutrition-plans/" + planId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/nutrition-plans/" + planId), ownerToken)).andExpect(status().isNotFound());
    }

    @Test
    void statusTransitionsMoveFreelyInBothDirections() throws Exception {
        String ownerToken = registerOwner("plan-status");
        String contactId = createContact(ownerToken, "Riley", "Client");
        String planId = createPlan(ownerToken, contactId);

        mockMvc.perform(authed(patch("/api/v1/nutrition-plans/" + planId + "/status"), ownerToken).content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));

        // Reactivating a plan a client resumes - a legitimate correction, same restraint
        // contracts.status/client_goals.status/training_sessions.status already take.
        mockMvc.perform(authed(patch("/api/v1/nutrition-plans/" + planId + "/status"), ownerToken).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void createNutritionPlan_withEndDateBeforeStartDate_isRejected() throws Exception {
        String ownerToken = registerOwner("plan-baddates");
        String contactId = createContact(ownerToken, "Alex", "Client");

        mockMvc.perform(authed(post("/api/v1/nutrition-plans"), ownerToken)
                        .content(
                                "{\"contactId\":\"%s\",\"title\":\"Bulking phase\",\"startDate\":\"2027-03-01\",\"endDate\":\"2027-01-01\"}"
                                        .formatted(contactId)))
                .andExpect(status().isBadRequest());
    }

    private String createContact(String token, String firstName, String lastName) throws Exception {
        MvcResult result = mockMvc
                .perform(authed(post("/api/v1/contacts"), token)
                        .content("{\"firstName\":\"%s\",\"lastName\":\"%s\"}".formatted(firstName, lastName)))
                .andExpect(status().isCreated())
                .andReturn();
        return readField(result, "data", "id");
    }

    private String createPlan(String token, String contactId) throws Exception {
        MvcResult result = mockMvc
                .perform(authed(post("/api/v1/nutrition-plans"), token)
                        .content("{\"contactId\":\"%s\",\"title\":\"Maintenance phase\"}".formatted(contactId)))
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
