package com.aitrainercrm.platform.clientgoal;

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
 * End-to-end coverage for the Client Goal module - see V36's migration comment and ClientGoal's
 * javadoc for the gap this fills (CourseEnrollment tracks progress through course content,
 * SalesGoal tracks internal rep quotas, Contract tracks legal terms - nothing else tracks a
 * client's own measurable objective). Covers full CRUD and the free (non-linear) status
 * transition with achievedAt stamped once and never overwritten, the same shape
 * ContractIntegrationTest already covers for signedAt.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ClientGoalIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void createUpdateListAndDeleteClientGoal_endToEnd() throws Exception {
        String ownerToken = registerOwner("goal-crud");
        String contactId = createContact(ownerToken, "Jamie", "Client");

        MvcResult createResult = mockMvc
                .perform(authed(post("/api/v1/client-goals"), ownerToken)
                        .content(
                                """
                                {"contactId":"%s","title":"Lose 15 lbs","goalType":"WEIGHT_LOSS","metricUnit":"lbs",
                                "startValue":200,"targetValue":185,"currentValue":200,"targetDate":"2027-01-01"}
                                """
                                        .formatted(contactId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.goalType").value("WEIGHT_LOSS"))
                .andExpect(jsonPath("$.data.achievedAt").doesNotExist())
                .andReturn();
        String goalId = readField(createResult, "data", "id");
        assertThat(goalId).isNotBlank();

        mockMvc.perform(authed(put("/api/v1/client-goals/" + goalId), ownerToken)
                        .content(
                                """
                                {"title":"Lose 15 lbs (revised)","goalType":"WEIGHT_LOSS","metricUnit":"lbs",
                                "startValue":200,"targetValue":180,"currentValue":195,"targetDate":"2027-02-01"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Lose 15 lbs (revised)"))
                .andExpect(jsonPath("$.data.currentValue").value(195));

        mockMvc.perform(authed(get("/api/v1/client-goals"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(authed(delete("/api/v1/client-goals/" + goalId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/client-goals/" + goalId), ownerToken)).andExpect(status().isNotFound());
    }

    @Test
    void statusTransitionToAchieved_stampsAchievedAtOnlyOnce() throws Exception {
        String ownerToken = registerOwner("goal-status");
        String contactId = createContact(ownerToken, "Riley", "Client");
        String goalId = createGoal(ownerToken, contactId, "Run a 5k");

        mockMvc.perform(authed(patch("/api/v1/client-goals/" + goalId + "/status"), ownerToken).content("{\"status\":\"ACHIEVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACHIEVED"))
                .andExpect(jsonPath("$.data.achievedAt").exists());

        MvcResult afterFirstAchievement = mockMvc.perform(authed(get("/api/v1/client-goals/" + goalId), ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        String achievedAtAfterFirst = readField(afterFirstAchievement, "data", "achievedAt");

        // Moving away and back to ACHIEVED - a legitimate correction, same restraint
        // contracts.status takes (see V35's migration comment) - must not overwrite achievedAt.
        mockMvc.perform(authed(patch("/api/v1/client-goals/" + goalId + "/status"), ownerToken).content("{\"status\":\"ON_HOLD\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(authed(patch("/api/v1/client-goals/" + goalId + "/status"), ownerToken).content("{\"status\":\"ACHIEVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.achievedAt").value(achievedAtAfterFirst));
    }

    private String createContact(String token, String firstName, String lastName) throws Exception {
        MvcResult result = mockMvc
                .perform(authed(post("/api/v1/contacts"), token)
                        .content("{\"firstName\":\"%s\",\"lastName\":\"%s\"}".formatted(firstName, lastName)))
                .andExpect(status().isCreated())
                .andReturn();
        return readField(result, "data", "id");
    }

    private String createGoal(String token, String contactId, String title) throws Exception {
        MvcResult result = mockMvc
                .perform(authed(post("/api/v1/client-goals"), token)
                        .content("{\"contactId\":\"%s\",\"title\":\"%s\",\"goalType\":\"ENDURANCE\"}".formatted(contactId, title)))
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
