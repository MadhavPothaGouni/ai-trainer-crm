package com.aitrainercrm.platform.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitrainercrm.platform.auth.dto.LoginRequest;
import com.aitrainercrm.platform.auth.dto.RegisterRequest;
import com.aitrainercrm.platform.support.AbstractIntegrationTest;
import com.aitrainercrm.platform.user.dto.CreateUserRequest;
import com.aitrainercrm.platform.user.entity.User;
import com.aitrainercrm.platform.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * End-to-end coverage for the room catalog and its owner-scoped bookings - see V53's migration
 * comment. Mirrors {@code LockerAssignmentIntegrationTest}'s shape for the catalog half (ROOM
 * isn't a core CRM resource, same as LOCKER/EQUIPMENT) and the owner-scoped booking half, plus a
 * dedicated case for the overlap-conflict rule {@code RoomBookingService#assertNoOverlap} adds.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class RoomBookingIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void roomCatalogAndBookingLifecycle_endToEnd() throws Exception {
        String ownerToken = registerOwner("room-crud");

        MvcResult roomResult = mockMvc
                .perform(authed(post("/api/v1/rooms"), ownerToken).content("{\"label\":\"Studio A\",\"location\":\"Main floor\",\"capacity\":12}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn();
        String roomId = readField(roomResult, "data", "id");
        assertThat(roomId).isNotBlank();

        Instant startsAt = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant endsAt = startsAt.plus(1, ChronoUnit.HOURS);

        MvcResult bookingResult = mockMvc
                .perform(authed(post("/api/v1/room-bookings"), ownerToken)
                        .content("{\"roomId\":\"%s\",\"purpose\":\"HIIT class\",\"startsAt\":\"%s\",\"endsAt\":\"%s\"}"
                                .formatted(roomId, startsAt, endsAt)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andReturn();
        String bookingId = readField(bookingResult, "data", "id");
        assertThat(bookingId).isNotBlank();

        // An overlapping second booking for the same room is rejected.
        Instant overlapStartsAt = startsAt.plus(30, ChronoUnit.MINUTES);
        Instant overlapEndsAt = overlapStartsAt.plus(1, ChronoUnit.HOURS);
        mockMvc.perform(authed(post("/api/v1/room-bookings"), ownerToken)
                        .content("{\"roomId\":\"%s\",\"purpose\":\"Yoga\",\"startsAt\":\"%s\",\"endsAt\":\"%s\"}"
                                .formatted(roomId, overlapStartsAt, overlapEndsAt)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ROOM_BOOKING_CONFLICT"));

        // A back-to-back (non-overlapping) booking is fine.
        Instant nextStartsAt = endsAt;
        Instant nextEndsAt = nextStartsAt.plus(1, ChronoUnit.HOURS);
        mockMvc.perform(authed(post("/api/v1/room-bookings"), ownerToken)
                        .content("{\"roomId\":\"%s\",\"purpose\":\"Pilates\",\"startsAt\":\"%s\",\"endsAt\":\"%s\"}"
                                .formatted(roomId, nextStartsAt, nextEndsAt)))
                .andExpect(status().isCreated());

        mockMvc.perform(authed(patch("/api/v1/room-bookings/" + bookingId + "/status"), ownerToken).content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        // Now that the original booking is cancelled, the overlapping slot is bookable.
        mockMvc.perform(authed(post("/api/v1/room-bookings"), ownerToken)
                        .content("{\"roomId\":\"%s\",\"purpose\":\"Now free\",\"startsAt\":\"%s\",\"endsAt\":\"%s\"}"
                                .formatted(roomId, overlapStartsAt, overlapEndsAt)))
                .andExpect(status().isCreated());

        // Re-confirming the original (cancelled) booking now conflicts with that new one.
        mockMvc.perform(authed(patch("/api/v1/room-bookings/" + bookingId + "/status"), ownerToken).content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ROOM_BOOKING_CONFLICT"));

        mockMvc.perform(authed(put("/api/v1/rooms/" + roomId), ownerToken)
                        .content("{\"label\":\"Studio A\",\"capacity\":12,\"status\":\"OUT_OF_SERVICE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OUT_OF_SERVICE"));

        mockMvc.perform(authed(get("/api/v1/room-bookings"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3));

        mockMvc.perform(authed(delete("/api/v1/room-bookings/" + bookingId), ownerToken)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/v1/room-bookings/" + bookingId), ownerToken)).andExpect(status().isNotFound());

        // --- A MEMBER teammate: ROOM isn't a core CRM resource, ROOM_BOOKING is. ---
        String teammateEmail = "room-teammate-%d@example.com".formatted(System.nanoTime());
        mockMvc.perform(authed(post("/api/v1/users"), ownerToken)
                        .content(objectMapper.writeValueAsString(new CreateUserRequest(teammateEmail, "New", "Teammate", null))))
                .andExpect(status().isCreated());
        String teammatePassword = "Str0ng!Passw0rd2";
        User teammate = userRepository.findByEmailAndDeletedAtIsNull(teammateEmail.trim().toLowerCase()).orElseThrow();
        teammate.setPasswordHash(passwordEncoder.encode(teammatePassword));
        userRepository.save(teammate);
        MvcResult teammateLoginResult = mockMvc
                .perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(teammateEmail, teammatePassword))))
                .andExpect(status().isOk())
                .andReturn();
        String teammateToken = readField(teammateLoginResult, "data", "accessToken");

        mockMvc.perform(authed(post("/api/v1/rooms"), teammateToken).content("{\"label\":\"Unauthorized\"}"))
                .andExpect(status().isForbidden());

        Instant teammateStartsAt = nextEndsAt;
        Instant teammateEndsAt = teammateStartsAt.plus(1, ChronoUnit.HOURS);
        mockMvc.perform(authed(post("/api/v1/room-bookings"), teammateToken)
                        .content("{\"roomId\":\"%s\",\"purpose\":\"Teammate session\",\"startsAt\":\"%s\",\"endsAt\":\"%s\"}"
                                .formatted(roomId, teammateStartsAt, teammateEndsAt)))
                .andExpect(status().isCreated());
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
