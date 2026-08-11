package com.aitrainercrm.platform.calendar.dto;

import com.aitrainercrm.platform.calendar.entity.CalendarEvent;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record CalendarEventDto(
        UUID id,
        String title,
        String description,
        String location,
        Instant startAt,
        Instant endAt,
        boolean allDay,
        CalendarEvent.RelatedToType relatedToType,
        UUID relatedToId,
        UUID ownerId,
        Instant createdAt,
        Instant updatedAt) {

    public static CalendarEventDto from(CalendarEvent event) {
        return CalendarEventDto.builder()
                .id(event.getId())
                .title(event.getTitle())
                .description(event.getDescription())
                .location(event.getLocation())
                .startAt(event.getStartAt())
                .endAt(event.getEndAt())
                .allDay(event.isAllDay())
                .relatedToType(event.getRelatedToType())
                .relatedToId(event.getRelatedToId())
                .ownerId(event.getOwnerId())
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .build();
    }
}
