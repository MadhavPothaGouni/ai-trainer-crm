package com.aitrainercrm.platform.calendar.dto;

import com.aitrainercrm.platform.calendar.entity.CalendarEvent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Same fields as CreateCalendarEventRequest minus ownerId - see UpdateTicketRequest for the identical reasoning (owner changes go through the dedicated assign-owner endpoint). */
public record UpdateCalendarEventRequest(
        @NotBlank @Size(max = 300) String title,
        @Size(max = 2000) String description,
        @Size(max = 255) String location,
        @NotNull Instant startAt,
        @NotNull Instant endAt,
        boolean allDay,
        CalendarEvent.RelatedToType relatedToType,
        UUID relatedToId) {
}
