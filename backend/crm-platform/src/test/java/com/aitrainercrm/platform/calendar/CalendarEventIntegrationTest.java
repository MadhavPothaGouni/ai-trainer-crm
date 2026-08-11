package com.aitrainercrm.platform.calendar;

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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
 * End-to-end coverage for the CalendarEvent module - the event itself plus its attendees
 * sub-resource. Covers full CRUD (relatedTo optional and both-or-neither validated), the
 * end-before-start business rule, attendee add/respond/remove with the exactly-one-of-two-targets
 * check, owner assignment, and CSV export.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CalendarEventIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void createUpdateListAndDeleteEvent_endToEnd() throws Exception {
        String ownerToken = registerOwner("event-crud");
        Instant start = Instant.parse("2026-09-01T15:00:00Z");
        Instant end = Instant.parse("2026-09-01T16:00:00Z");

        MvcResult createResult = mockMvc
                .perform(authed(post("/api/v1/calendar-events"), ownerToken)
                        .content(
                                """
                                {"title":"Team standup","startAt":"%s","endAt":"%s","allDay":false}
                                """
                                        .formatted(start, end)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("Team standup"))
                .andExpect(jsonPath("$.data.relatedToType").doesNotExist())
                .andReturn();
        String eventId = readField(createResult, "data", "id");
        assertThat(eventId).isNotBlank();

        mockMvc.perform(authed(put("/api/v1/calendar-events/" + eventId), ownerToken)
                        .content(
                                """
                                {"title":"Team standup (moved)","startAt":"%s","endAt":"%s","allDay":false}
                                """
                                        .formatted(start, end)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Team standup (moved)"));

        mockMvc.perform(authed(get("/api/v1/calendar-events"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(authed(delete("/api/v1/calendar-events/" + eventId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/calendar-events/" + eventId), ownerToken)).andExpect(status().isNotFound());
    }

    @Test
    void createEvent_withEndBeforeStart_returns400() throws Exception {
        String ownerToken = registerOwner("event-badtimes");

        mockMvc.perform(authed(post("/api/v1/calendar-events"), ownerToken)
                        .content(
                                """
                                {"title":"Time travel","startAt":"2026-09-01T16:00:00Z","endAt":"2026-09-01T15:00:00Z","allDay":false}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createEvent_withOnlyRelatedToTypeSet_returns400() throws Exception {
        String ownerToken = registerOwner("event-partialrelated");

        mockMvc.perform(authed(post("/api/v1/calendar-events"), ownerToken)
                        .content(
                                """
                                {"title":"Orphan link","startAt":"2026-09-01T15:00:00Z","endAt":"2026-09-01T16:00:00Z",
                                 "allDay":false,"relatedToType":"ACCOUNT"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createEvent_withUnknownRelatedRecord_returns404() throws Exception {
        String ownerToken = registerOwner("event-badrelated");

        mockMvc.perform(authed(post("/api/v1/calendar-events"), ownerToken)
                        .content(
                                """
                                {"title":"Kickoff","startAt":"2026-09-01T15:00:00Z","endAt":"2026-09-01T16:00:00Z",
                                 "allDay":false,"relatedToType":"ACCOUNT","relatedToId":"%s"}
                                """
                                        .formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void attendees_addRespondAndRemove_withExactlyOneTargetEnforced() throws Exception {
        String ownerToken = registerOwner("event-attendees");
        String eventId = createEvent(ownerToken, "Planning session");

        MvcResult meResult = mockMvc.perform(authed(get("/api/v1/users/me"), ownerToken)).andExpect(status().isOk()).andReturn();
        String userId = readField(meResult, "data", "id");

        mockMvc.perform(authed(post("/api/v1/calendar-events/" + eventId + "/attendees"), ownerToken)
                        .content("{\"userId\":null,\"externalEmail\":null}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(authed(post("/api/v1/calendar-events/" + eventId + "/attendees"), ownerToken)
                        .content("{\"userId\":\"" + userId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.responseStatus").value("NEEDS_ACTION"))
                .andReturn();

        mockMvc.perform(authed(post("/api/v1/calendar-events/" + eventId + "/attendees"), ownerToken)
                        .content("{\"userId\":\"" + userId + "\"}"))
                .andExpect(status().isConflict());

        MvcResult guestResult = mockMvc
                .perform(authed(post("/api/v1/calendar-events/" + eventId + "/attendees"), ownerToken)
                        .content("{\"externalEmail\":\"guest@example.com\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String guestAttendeeId = readField(guestResult, "data", "id");

        mockMvc.perform(authed(get("/api/v1/calendar-events/" + eventId + "/attendees"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        mockMvc.perform(authed(patch("/api/v1/calendar-events/" + eventId + "/attendees/" + guestAttendeeId + "/response"), ownerToken)
                        .content("{\"responseStatus\":\"ACCEPTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.responseStatus").value("ACCEPTED"));

        mockMvc.perform(authed(delete("/api/v1/calendar-events/" + eventId + "/attendees/" + guestAttendeeId), ownerToken))
                .andExpect(status().isOk());

        mockMvc.perform(authed(get("/api/v1/calendar-events/" + eventId + "/attendees"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void assignOwner_movesTheEventToTheNewOwner() throws Exception {
        String ownerToken = registerOwner("event-assign");
        String eventId = createEvent(ownerToken, "Sync");

        MvcResult meResult = mockMvc.perform(authed(get("/api/v1/users/me"), ownerToken)).andExpect(status().isOk()).andReturn();
        String ownerId = readField(meResult, "data", "id");

        mockMvc.perform(authed(patch("/api/v1/calendar-events/" + eventId + "/owner"), ownerToken).content("{\"ownerId\":\"" + ownerId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ownerId").value(ownerId));
    }

    @Test
    void exportEvents_returnsCsvOfVisibleEvents() throws Exception {
        String ownerToken = registerOwner("event-export");
        createEvent(ownerToken, "Export me");

        MvcResult exportResult =
                mockMvc.perform(authed(get("/api/v1/calendar-events/export"), ownerToken)).andExpect(status().isOk()).andReturn();
        String body = new String(exportResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(body).contains("Export me").contains("Title,Location,Start At,End At,All Day,Related To Type,Related To Id,Created At");
    }

    private String createEvent(String token, String title) throws Exception {
        MvcResult result = mockMvc
                .perform(authed(post("/api/v1/calendar-events"), token)
                        .content(
                                """
                                {"title":"%s","startAt":"2026-09-01T15:00:00Z","endAt":"2026-09-01T16:00:00Z","allDay":false}
                                """
                                        .formatted(title)))
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
