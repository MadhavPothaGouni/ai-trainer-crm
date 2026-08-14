package com.aitrainercrm.platform.exercise;

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
 * End-to-end coverage for the Exercise module - see V38's migration comment and Exercise's
 * javadoc for the gap this fills (the atomic movement-library building block a coach references
 * when planning, distinct from Course's structured curriculum content and TrainingSession's
 * post-session log). Covers full CRUD, the unpaginated active-catalog endpoint (same shape
 * CourseIntegrationTest already covers), and the per-organization case-insensitive name
 * uniqueness constraint.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ExerciseIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void createUpdateListAndDeleteExercise_endToEnd() throws Exception {
        String ownerToken = registerOwner("exercise-crud");

        MvcResult createResult = mockMvc
                .perform(authed(post("/api/v1/exercises"), ownerToken)
                        .content(
                                """
                                {"name":"Barbell Back Squat","description":"Classic lower-body strength movement",
                                "category":"STRENGTH","primaryMuscleGroup":"LEGS","equipment":"BARBELL","difficultyLevel":"INTERMEDIATE"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Barbell Back Squat"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andReturn();
        String exerciseId = readField(createResult, "data", "id");
        assertThat(exerciseId).isNotBlank();

        mockMvc.perform(authed(put("/api/v1/exercises/" + exerciseId), ownerToken)
                        .content(
                                """
                                {"name":"Barbell Back Squat","description":"Updated cues","category":"STRENGTH",
                                "primaryMuscleGroup":"LEGS","equipment":"BARBELL","difficultyLevel":"ADVANCED","active":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.difficultyLevel").value("ADVANCED"));

        mockMvc.perform(authed(get("/api/v1/exercises"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(authed(get("/api/v1/exercises/active"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(authed(delete("/api/v1/exercises/" + exerciseId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/exercises/" + exerciseId), ownerToken)).andExpect(status().isNotFound());
    }

    @Test
    void createExercise_duplicateNameCaseInsensitiveInSameOrganization_isRejected() throws Exception {
        String ownerToken = registerOwner("exercise-dupe");
        mockMvc.perform(authed(post("/api/v1/exercises"), ownerToken)
                        .content(
                                "{\"name\":\"Push Up\",\"category\":\"STRENGTH\",\"primaryMuscleGroup\":\"CHEST\","
                                        + "\"equipment\":\"BODYWEIGHT\",\"difficultyLevel\":\"BEGINNER\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(authed(post("/api/v1/exercises"), ownerToken)
                        .content(
                                "{\"name\":\"push up\",\"category\":\"STRENGTH\",\"primaryMuscleGroup\":\"CHEST\","
                                        + "\"equipment\":\"BODYWEIGHT\",\"difficultyLevel\":\"BEGINNER\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void listActiveExercises_excludesInactiveOnes() throws Exception {
        String ownerToken = registerOwner("exercise-active");
        MvcResult createResult = mockMvc
                .perform(authed(post("/api/v1/exercises"), ownerToken)
                        .content(
                                "{\"name\":\"Kettlebell Swing\",\"category\":\"CARDIO\",\"primaryMuscleGroup\":\"FULL_BODY\","
                                        + "\"equipment\":\"KETTLEBELL\",\"difficultyLevel\":\"BEGINNER\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String exerciseId = readField(createResult, "data", "id");

        mockMvc.perform(authed(put("/api/v1/exercises/" + exerciseId), ownerToken)
                        .content(
                                "{\"name\":\"Kettlebell Swing\",\"category\":\"CARDIO\",\"primaryMuscleGroup\":\"FULL_BODY\","
                                        + "\"equipment\":\"KETTLEBELL\",\"difficultyLevel\":\"BEGINNER\",\"active\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(authed(get("/api/v1/exercises/active"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
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
