package com.aitrainercrm.platform.booking.dto;

import com.aitrainercrm.platform.booking.entity.BookingSlot;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record BookingSlotDto(
        UUID id,
        Instant startAt,
        Instant endAt,
        BookingSlot.Status status,
        BookingSlot.TargetType targetType,
        UUID targetId,
        Instant bookedAt,
        UUID calendarEventId) {

    public static BookingSlotDto from(BookingSlot slot) {
        return BookingSlotDto.builder()
                .id(slot.getId())
                .startAt(slot.getStartAt())
                .endAt(slot.getEndAt())
                .status(slot.getStatus())
                .targetType(slot.getTargetType())
                .targetId(slot.getTargetId())
                .bookedAt(slot.getBookedAt())
                .calendarEventId(slot.getCalendarEventId())
                .build();
    }
}
