package com.aitrainercrm.platform.booking;

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
 * End-to-end coverage for BookingLink/BookingSlot - see V33's migration comment for the module
 * overview. {@code BookingLinkServiceTest} covers the endAt-snapshot math and the book/cancel
 * transitions with a mocked CalendarEventService; this pins down what only real HTTP + a real
 * Postgres + the real (not mocked) CalendarEventService can: the actual BOOKING_LINK permission
 * grants an OWNER role has, the real uq_booking_links_org_slug / uq_booking_slots_link_start
 * constraints, and that booking a slot creates a real, independently-fetchable CalendarEvent -
 * the cross-module integration this whole module exists for.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class BookingLinkIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void bookingLink_endToEnd_bookingASlotCreatesARealCalendarEvent() throws Exception {
        String ownerToken = registerOwner("booking-flow-owner");

        MvcResult linkResult = mockMvc
                .perform(authed(post("/api/v1/booking-links"), ownerToken)
                        .content("{\"title\":\"Discovery Call\",\"durationMinutes\":30,\"slug\":\"discovery-call\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.slots.length()").value(0))
                .andReturn();
        String linkId = readField(linkResult, "data", "id");

        // A second link with the same slug in the same org is rejected by the real
        // uq_booking_links_org_slug constraint (V33), not just an in-memory check.
        mockMvc.perform(authed(post("/api/v1/booking-links"), ownerToken)
                        .content("{\"title\":\"Discovery Call 2\",\"durationMinutes\":15,\"slug\":\"discovery-call\"}"))
                .andExpect(status().isConflict());

        MvcResult slotResult = mockMvc
                .perform(authed(post("/api/v1/booking-links/" + linkId + "/slots"), ownerToken)
                        .content("{\"startAt\":\"2026-09-01T15:00:00Z\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.endAt").value("2026-09-01T15:30:00Z"))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andReturn();
        String slotId = readField(slotResult, "data", "id");

        // Re-adding a slot at the exact same start time is rejected by the real
        // uq_booking_slots_link_start constraint.
        mockMvc.perform(authed(post("/api/v1/booking-links/" + linkId + "/slots"), ownerToken)
                        .content("{\"startAt\":\"2026-09-01T15:00:00Z\"}"))
                .andExpect(status().isConflict());

        String leadId = createLead(ownerToken, "Jordan", "Prospect", "jordan@example.com");

        MvcResult bookResult = mockMvc
                .perform(authed(patch("/api/v1/booking-links/" + linkId + "/slots/" + slotId + "/book"), ownerToken)
                        .content("{\"targetType\":\"LEAD\",\"targetId\":\"" + leadId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("BOOKED"))
                .andReturn();
        String calendarEventId = readField(bookResult, "data", "calendarEventId");

        // The booking really did create a fetchable CalendarEvent - the whole point of this module.
        mockMvc.perform(authed(get("/api/v1/calendar-events/" + calendarEventId), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Discovery Call"))
                .andExpect(jsonPath("$.data.relatedToType").value("LEAD"));

        // Booking an already-booked slot is rejected.
        mockMvc.perform(authed(patch("/api/v1/booking-links/" + linkId + "/slots/" + slotId + "/book"), ownerToken)
                        .content("{\"targetType\":\"LEAD\",\"targetId\":\"" + leadId + "\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(authed(patch("/api/v1/booking-links/" + linkId + "/slots/" + slotId + "/cancel"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.targetId").value(leadId));

        // The CalendarEvent was soft-deleted along with the cancellation - no longer fetchable.
        mockMvc.perform(authed(get("/api/v1/calendar-events/" + calendarEventId), ownerToken))
                .andExpect(status().isNotFound());
    }

    private String createLead(String token, String firstName, String lastName, String email) throws Exception {
        MvcResult result = mockMvc
                .perform(authed(post("/api/v1/leads"), token)
                        .content("{\"firstName\":\"" + firstName + "\",\"lastName\":\"" + lastName + "\",\"email\":\"" + email
                                + "\",\"source\":\"WEBSITE\"}"))
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
