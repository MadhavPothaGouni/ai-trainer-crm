package com.aitrainercrm.platform.calendar.dto;

import com.aitrainercrm.platform.calendar.entity.CalendarEvent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record CreateCalendarEventRequest(
        @NotBlank @Size(max = 300) String title,
        @Size(max = 2000) String description,
        @Size(max = 255) String location,
        @NotNull Instant startAt,
        @NotNull Instant endAt,
        boolean allDay,

        /** Both null is fine - not every event is about a CRM record. Non-null relatedToType requires a non-null relatedToId (and vice versa) - validated in CalendarEventService, same reasoning ActivityService#validateRelatedTo documents for a required version of this pair. */
        CalendarEvent.RelatedToType relatedToType,
        UUID relatedToId,

        /** Null defaults to the creator - see AccountService#resolveOwner for the identical rule applied here. */
        UUID ownerId) {
}
