package com.aitrainercrm.platform.dashboard;

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
 * End-to-end coverage for Dashboards: creating one, attaching a widget per
 * {@code DashboardWidget.ReportType}, and fetching {@code /data} to verify
 * each widget's live data actually comes from the real {@code ReportService}
 * methods (not a placeholder) - a zero-filled pipeline-by-stage array, a
 * zero-filled lead-funnel array, and an empty leaderboard array for a
 * brand-new organization with no opportunities/leads yet, matching
 * {@code ReportService}'s own documented zero-fill/empty-when-nothing-to-
 * rank behavior. Also covers the default-dashboard toggle (MANAGE-gated)
 * and widget update/remove.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class DashboardIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void dashboardWithAllThreeWidgetTypes_returnsLiveReportData_endToEnd() throws Exception {
        String ownerToken = registerOwner("dashboard-owner");

        MvcResult dashboardResult = mockMvc
                .perform(authed(post("/api/v1/dashboards"), ownerToken).content("{\"name\":\"Sales overview\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.isDefault").value(false))
                .andReturn();
        UUID dashboardId = UUID.fromString(readField(dashboardResult, "data", "id"));

        mockMvc.perform(authed(post("/api/v1/dashboards/" + dashboardId + "/widgets"), ownerToken)
                        .content("{\"reportType\":\"PIPELINE_BY_STAGE\",\"displayOrder\":0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("Pipeline by stage"));

        MvcResult funnelWidgetResult = mockMvc
                .perform(authed(post("/api/v1/dashboards/" + dashboardId + "/widgets"), ownerToken)
                        .content("{\"reportType\":\"LEAD_FUNNEL\",\"title\":\"My custom title\",\"displayOrder\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("My custom title"))
                .andReturn();
        UUID funnelWidgetId = UUID.fromString(readField(funnelWidgetResult, "data", "id"));

        mockMvc.perform(authed(post("/api/v1/dashboards/" + dashboardId + "/widgets"), ownerToken)
                        .content("{\"reportType\":\"LEADERBOARD\",\"displayOrder\":2}"))
                .andExpect(status().isCreated());

        // --- The dashboard shell (get) lists widget defs, no data ---
        mockMvc.perform(authed(get("/api/v1/dashboards/" + dashboardId), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.widgets.length()").value(3));

        // --- /data composes each widget's live numbers from the real ReportService ---
        mockMvc.perform(authed(get("/api/v1/dashboards/" + dashboardId + "/data"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.widgets.length()").value(3))
                .andExpect(jsonPath("$.data.widgets[0].reportType").value("PIPELINE_BY_STAGE"))
                .andExpect(jsonPath("$.data.widgets[0].data").isArray())
                .andExpect(jsonPath("$.data.widgets[0].data[0].stage").exists())
                .andExpect(jsonPath("$.data.widgets[1].reportType").value("LEAD_FUNNEL"))
                .andExpect(jsonPath("$.data.widgets[1].data[0].status").exists())
                .andExpect(jsonPath("$.data.widgets[2].reportType").value("LEADERBOARD"))
                .andExpect(jsonPath("$.data.widgets[2].data.length()").value(0)); // no opportunities yet - nobody to rank

        // --- Update a widget's title/layout - reportType itself has no field to change ---
        mockMvc.perform(authed(put("/api/v1/dashboards/" + dashboardId + "/widgets/" + funnelWidgetId), ownerToken)
                        .content("{\"title\":\"Renamed funnel\",\"displayOrder\":1,\"width\":12,\"height\":6}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Renamed funnel"))
                .andExpect(jsonPath("$.data.width").value(12));

        // --- Set as default ---
        mockMvc.perform(authed(post("/api/v1/dashboards/" + dashboardId + "/default"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDefault").value(true));

        // --- Remove a widget ---
        mockMvc.perform(authed(delete("/api/v1/dashboards/" + dashboardId + "/widgets/" + funnelWidgetId), ownerToken))
                .andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/dashboards/" + dashboardId), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.widgets.length()").value(2));
    }

    @Test
    void secondDashboardAsDefault_unsetsThePrevious_andCrudWorksEndToEnd() throws Exception {
        String ownerToken = registerOwner("dashboard-default");

        MvcResult firstResult = mockMvc
                .perform(authed(post("/api/v1/dashboards"), ownerToken).content("{\"name\":\"First\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID firstId = UUID.fromString(readField(firstResult, "data", "id"));

        MvcResult secondResult = mockMvc
                .perform(authed(post("/api/v1/dashboards"), ownerToken).content("{\"name\":\"Second\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID secondId = UUID.fromString(readField(secondResult, "data", "id"));

        mockMvc.perform(authed(post("/api/v1/dashboards/" + firstId + "/default"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDefault").value(true));
        mockMvc.perform(authed(post("/api/v1/dashboards/" + secondId + "/default"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDefault").value(true));

        mockMvc.perform(authed(get("/api/v1/dashboards/" + firstId), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDefault").value(false));

        mockMvc.perform(authed(put("/api/v1/dashboards/" + firstId), ownerToken).content("{\"name\":\"First (renamed)\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("First (renamed)"));

        mockMvc.perform(authed(get("/api/v1/dashboards"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        mockMvc.perform(authed(delete("/api/v1/dashboards/" + firstId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/dashboards/" + firstId), ownerToken)).andExpect(status().isNotFound());
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
