package com.aitrainercrm.platform.room.dto;

import com.aitrainercrm.platform.room.entity.RoomBooking;
import java.time.Instant;
import java.util.UUID;

public record RoomBookingDto(
        UUID id,
        UUID roomId,
        UUID ownerId,
        String purpose,
        Instant startsAt,
        Instant endsAt,
        RoomBooking.Status status,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static RoomBookingDto from(RoomBooking booking) {
        return new RoomBookingDto(
                booking.getId(),
                booking.getRoomId(),
                booking.getOwnerId(),
                booking.getPurpose(),
                booking.getStartsAt(),
                booking.getEndsAt(),
                booking.getStatus(),
                booking.getNotes(),
                booking.getCreatedAt(),
                booking.getUpdatedAt());
    }
}
