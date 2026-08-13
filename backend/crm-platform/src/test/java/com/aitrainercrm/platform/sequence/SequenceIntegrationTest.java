package com.aitrainercrm.platform.sequence;

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
 * End-to-end coverage for the Sequence/SequenceStep/SequenceEnrollment trio - see V32's migration
 * comment for the module overview. {@code SequenceEnrollmentServiceTest} covers the advance/
 * auto-complete and resolveOwner math with mocks; this pins down what only real HTTP + real
 * Postgres can: the real SEQUENCE/SEQUENCE_ENROLLMENT permission grants an OWNER role actually
 * has, the real {@code uq_sequence_enrollments_target_active} constraint, and the step sub-resource
 * endpoints against a real cascading FK.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class SequenceIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void sequence_endToEnd_stepsAndEnrollmentAdvanceToCompletion() throws Exception {
        String ownerToken = registerOwner("sequence-flow-owner");

        MvcResult sequenceResult = mockMvc
                .perform(authed(post("/api/v1/sequences"), ownerToken).content("{\"name\":\"New Lead Outreach\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.steps.length()").value(0))
                .andReturn();
        String sequenceId = readField(sequenceResult, "data", "id");

        mockMvc.perform(authed(post("/api/v1/sequences/" + sequenceId + "/steps"), ownerToken)
                        .content("{\"type\":\"EMAIL\",\"dayOffset\":0,\"subject\":\"Intro\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.stepOrder").value(0));
        mockMvc.perform(authed(post("/api/v1/sequences/" + sequenceId + "/steps"), ownerToken)
                        .content("{\"type\":\"CALL\",\"dayOffset\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.stepOrder").value(1));

        mockMvc.perform(authed(get("/api/v1/sequences/" + sequenceId), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.steps.length()").value(2));

        String leadId = createLead(ownerToken, "Jordan", "Prospect");

        MvcResult enrollResult = mockMvc
                .perform(authed(post("/api/v1/sequence-enrollments"), ownerToken)
                        .content("{\"sequenceId\":\"" + sequenceId + "\",\"targetType\":\"LEAD\",\"targetId\":\"" + leadId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.currentStepIndex").value(0))
                .andReturn();
        String enrollmentId = readField(enrollResult, "data", "id");

        // Re-enrolling the same lead in the same sequence while the first enrollment is still active
        // is rejected - the real uq_sequence_enrollments_target_active constraint (V32), not just an
        // in-memory check.
        mockMvc.perform(authed(post("/api/v1/sequence-enrollments"), ownerToken)
                        .content("{\"sequenceId\":\"" + sequenceId + "\",\"targetType\":\"LEAD\",\"targetId\":\"" + leadId + "\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(authed(patch("/api/v1/sequence-enrollments/" + enrollmentId + "/advance"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentStepIndex").value(1))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        // Walking off the end of the two-step sequence auto-completes rather than requiring a
        // separate "mark complete" call - see SequenceEnrollmentService#advance's javadoc.
        mockMvc.perform(authed(patch("/api/v1/sequence-enrollments/" + enrollmentId + "/advance"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentStepIndex").value(2))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        // COMPLETED is never set directly through the status endpoint.
        mockMvc.perform(authed(patch("/api/v1/sequence-enrollments/" + enrollmentId + "/status"), ownerToken)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sequence_enrollmentAgainstUnknownLead_isNotFound() throws Exception {
        String ownerToken = registerOwner("sequence-badtarget-owner");
        MvcResult sequenceResult = mockMvc
                .perform(authed(post("/api/v1/sequences"), ownerToken).content("{\"name\":\"Renewal Nudge\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String sequenceId = readField(sequenceResult, "data", "id");

        mockMvc.perform(authed(post("/api/v1/sequence-enrollments"), ownerToken)
                        .content("{\"sequenceId\":\"" + sequenceId + "\",\"targetType\":\"LEAD\",\"targetId\":\""
                                + java.util.UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound());
    }

    private String createLead(String token, String firstName, String lastName) throws Exception {
        MvcResult result = mockMvc
                .perform(authed(post("/api/v1/leads"), token)
                        .content("{\"firstName\":\"" + firstName + "\",\"lastName\":\"" + lastName + "\",\"source\":\"WEBSITE\"}"))
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
