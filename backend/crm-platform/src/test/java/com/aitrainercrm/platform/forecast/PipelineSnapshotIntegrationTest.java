package com.aitrainercrm.platform.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitrainercrm.platform.account.dto.CreateAccountRequest;
import com.aitrainercrm.platform.auth.dto.LoginRequest;
import com.aitrainercrm.platform.auth.dto.RegisterRequest;
import com.aitrainercrm.platform.forecast.service.PipelineSnapshotService;
import com.aitrainercrm.platform.opportunity.dto.CreateOpportunityRequest;
import com.aitrainercrm.platform.support.AbstractIntegrationTest;
import com.aitrainercrm.platform.user.dto.CreateUserRequest;
import com.aitrainercrm.platform.user.entity.User;
import com.aitrainercrm.platform.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * End-to-end coverage for what an HTTP test can actually exercise: a real opportunity captured
 * into a real {@code PipelineSnapshot} row (via {@link PipelineSnapshotService#capture}, called
 * directly rather than waiting on the cron trigger - same reasoning {@code
 * SlaEscalationIntegrationTest} backdates a ticket instead of sleeping a full SLA target),
 * showing up correctly through both read endpoints, the invalid-range 400, and REPORT:READ gating
 * a default MEMBER out entirely (same as {@code ReportIntegrationTest} for the endpoints this
 * module deliberately reuses that permission from). The capture/fold algorithm's own branch
 * coverage (multi-owner, multi-stage, multi-day folding; idempotent re-capture) lives in {@code
 * PipelineSnapshotServiceTest} instead.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class PipelineSnapshotIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PipelineSnapshotService pipelineSnapshotService;

    @Test
    void captureThenRead_snapshotsAndTrendReflectTheRealOpportunity() throws Exception {
        String ownerToken = registerOwner("forecast-owner");

        MvcResult accountResult = mockMvc
                .perform(authed(post("/api/v1/accounts"), ownerToken)
                        .content(objectMapper.writeValueAsString(
                                new CreateAccountRequest("Acme Forecasting", null, null, null, null, null, null, null, null, null, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID accountId = UUID.fromString(readField(accountResult, "data", "id"));

        CreateOpportunityRequest opportunity =
                new CreateOpportunityRequest(accountId, null, "Forecast deal", new BigDecimal("2500.00"), null, null, null, null);
        mockMvc.perform(authed(post("/api/v1/opportunities"), ownerToken).content(objectMapper.writeValueAsString(opportunity)))
                .andExpect(status().isCreated());

        LocalDate today = LocalDate.now();
        int captured = pipelineSnapshotService.capture(today);
        assertThat(captured).isGreaterThanOrEqualTo(1);

        MvcResult snapshotsResult = mockMvc
                .perform(authed(get("/api/v1/forecast/snapshots").param("from", today.toString()).param("to", today.toString()), ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode snapshots = objectMapper.readTree(snapshotsResult.getResponse().getContentAsString()).get("data");
        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.get(0).get("stage").asText()).isEqualTo("PROSPECTING");
        assertThat(snapshots.get(0).get("dealCount").asInt()).isEqualTo(1);
        assertThat(snapshots.get(0).get("totalValue").decimalValue()).isEqualByComparingTo("2500.00");

        MvcResult trendResult = mockMvc
                .perform(authed(get("/api/v1/forecast/trend").param("from", today.toString()).param("to", today.toString()), ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode trend = objectMapper.readTree(trendResult.getResponse().getContentAsString()).get("data");
        assertThat(trend).hasSize(1);
        assertThat(trend.get(0).get("date").asText()).isEqualTo(today.toString());
        assertThat(trend.get(0).get("dealCount").asInt()).isEqualTo(1);
        assertThat(trend.get(0).get("totalValue").decimalValue()).isEqualByComparingTo("2500.00");
        assertThat(trend.get(0).get("valueByStage").get("PROSPECTING").decimalValue()).isEqualByComparingTo("2500.00");
    }

    @Test
    void recapturingTheSameDate_replacesRatherThanDuplicates() throws Exception {
        String ownerToken = registerOwner("forecast-recapture-owner");
        MvcResult accountResult = mockMvc
                .perform(authed(post("/api/v1/accounts"), ownerToken)
                        .content(objectMapper.writeValueAsString(
                                new CreateAccountRequest("Acme Recapture", null, null, null, null, null, null, null, null, null, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID accountId = UUID.fromString(readField(accountResult, "data", "id"));
        mockMvc.perform(authed(post("/api/v1/opportunities"), ownerToken)
                        .content(objectMapper.writeValueAsString(
                                new CreateOpportunityRequest(accountId, null, "Deal", new BigDecimal("100.00"), null, null, null, null))))
                .andExpect(status().isCreated());

        LocalDate today = LocalDate.now();
        pipelineSnapshotService.capture(today);
        pipelineSnapshotService.capture(today); // re-run for the same day, e.g. a redeploy re-triggering the cron

        MvcResult result = mockMvc
                .perform(authed(get("/api/v1/forecast/snapshots").param("from", today.toString()).param("to", today.toString()), ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode snapshots = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertThat(snapshots).hasSize(1);
    }

    @Test
    void invalidDateRange_returns400() throws Exception {
        String ownerToken = registerOwner("forecast-badrange-owner");
        LocalDate today = LocalDate.now();

        mockMvc.perform(authed(get("/api/v1/forecast/snapshots")
                        .param("from", today.toString())
                        .param("to", today.minusDays(1).toString()), ownerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void memberWithoutReportPermission_forbiddenOnBothEndpoints() throws Exception {
        String ownerToken = registerOwner("forecast-scope-owner");
        String teammateEmail = "forecast-scope-teammate-%d@example.com".formatted(System.nanoTime());
        mockMvc.perform(authed(post("/api/v1/users"), ownerToken)
                        .content(objectMapper.writeValueAsString(new CreateUserRequest(teammateEmail, "New", "Teammate", null))))
                .andExpect(status().isCreated());
        String password = "Str0ng!Passw0rd2";
        User teammate = userRepository.findByEmailAndDeletedAtIsNull(teammateEmail.trim().toLowerCase()).orElseThrow();
        teammate.setPasswordHash(passwordEncoder.encode(password));
        userRepository.save(teammate);
        MvcResult loginResult = mockMvc
                .perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(teammateEmail, password))))
                .andExpect(status().isOk())
                .andReturn();
        String teammateToken = readField(loginResult, "data", "accessToken");

        LocalDate today = LocalDate.now();
        mockMvc.perform(authed(get("/api/v1/forecast/snapshots").param("from", today.toString()).param("to", today.toString()), teammateToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(authed(get("/api/v1/forecast/trend").param("from", today.toString()).param("to", today.toString()), teammateToken))
                .andExpect(status().isForbidden());
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
