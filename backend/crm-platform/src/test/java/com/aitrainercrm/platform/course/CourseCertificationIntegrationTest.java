package com.aitrainercrm.platform.course;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * End-to-end coverage for the Course/CourseEnrollment and Certification/UserCertification pairs -
 * see V31's migration comment for the module overview. {@code CourseEnrollmentServiceTest}/{@code
 * UserCertificationServiceTest} cover the passing-threshold and expiry-computation math with mocks;
 * this pins down what only real HTTP + real Postgres can: the actual COURSE/COURSE_ENROLLMENT/
 * CERTIFICATION/USER_CERTIFICATION permission grants an OWNER role really has, and the real
 * {@code uq_course_enrollments_course_user_active} constraint backing the duplicate-enrollment
 * rejection.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CourseCertificationIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void courseEnrollment_endToEnd_scoreAbovePassingBarCompletes() throws Exception {
        String ownerToken = registerOwner("course-flow-owner");

        MvcResult courseResult = mockMvc
                .perform(authed(post("/api/v1/courses"), ownerToken)
                        .content("{\"title\":\"Objection Handling 101\",\"category\":\"SALES\",\"durationMinutes\":45,\"passingScorePercent\":70}"))
                .andExpect(status().isCreated())
                .andReturn();
        String courseId = readField(courseResult, "data", "id");

        mockMvc.perform(authed(get("/api/v1/courses/active"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        MvcResult enrollResult = mockMvc
                .perform(authed(post("/api/v1/course-enrollments"), ownerToken).content("{\"courseId\":\"" + courseId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("NOT_STARTED"))
                .andReturn();
        String enrollmentId = readField(enrollResult, "data", "id");

        // Re-enrolling in the same course while the first enrollment is still active is rejected -
        // the real uq_course_enrollments_course_user_active constraint (V31), not just an in-memory check.
        mockMvc.perform(authed(post("/api/v1/course-enrollments"), ownerToken).content("{\"courseId\":\"" + courseId + "\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(authed(patch("/api/v1/course-enrollments/" + enrollmentId + "/progress"), ownerToken)
                        .content("{\"status\":\"COMPLETED\",\"scorePercent\":85}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.scorePercent").value(85));
    }

    @Test
    void courseEnrollment_scoreBelowPassingBar_isReportedAsFailed() throws Exception {
        String ownerToken = registerOwner("course-fail-owner");
        String courseId = createCourse(ownerToken, "Compliance Basics", "COMPLIANCE", 80);
        String enrollmentId = enroll(ownerToken, courseId);

        mockMvc.perform(authed(patch("/api/v1/course-enrollments/" + enrollmentId + "/progress"), ownerToken)
                        .content("{\"status\":\"COMPLETED\",\"scorePercent\":50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"));
    }

    @Test
    void certificationAward_computesExpiryFromValidityMonths() throws Exception {
        String ownerToken = registerOwner("cert-flow-owner");

        MvcResult certResult = mockMvc
                .perform(authed(post("/api/v1/certifications"), ownerToken)
                        .content("{\"name\":\"Certified Solutions Consultant\",\"issuingBody\":\"Internal\",\"validityMonths\":12}"))
                .andExpect(status().isCreated())
                .andReturn();
        String certificationId = readField(certResult, "data", "id");

        mockMvc.perform(authed(post("/api/v1/user-certifications"), ownerToken)
                        .content("{\"certificationId\":\"" + certificationId + "\",\"earnedAt\":\"2026-01-15\",\"credentialNumber\":\"CSC-001\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.expiresAt").value("2027-01-15"))
                .andExpect(jsonPath("$.data.expired").value(false));

        mockMvc.perform(authed(get("/api/v1/certifications/active"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    private String createCourse(String token, String title, String category, int passingScorePercent) throws Exception {
        MvcResult result = mockMvc
                .perform(authed(post("/api/v1/courses"), token)
                        .content("{\"title\":\"" + title + "\",\"category\":\"" + category + "\",\"durationMinutes\":30,\"passingScorePercent\":"
                                + passingScorePercent + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        return readField(result, "data", "id");
    }

    private String enroll(String token, String courseId) throws Exception {
        MvcResult result = mockMvc
                .perform(authed(post("/api/v1/course-enrollments"), token).content("{\"courseId\":\"" + courseId + "\"}"))
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
