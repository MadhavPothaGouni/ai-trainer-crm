package com.aitrainercrm.platform.checkin.dto;

import com.aitrainercrm.platform.checkin.entity.ClientCheckIn;
import java.time.Instant;
import java.util.UUID;

public record ClientCheckInDto(
        UUID id,
        UUID contactId,
        UUID ownerId,
        Instant checkedInAt,
        ClientCheckIn.Status status,
        Instant checkedOutAt,
        ClientCheckIn.Method method,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static ClientCheckInDto from(ClientCheckIn checkIn) {
        return new ClientCheckInDto(
                checkIn.getId(),
                checkIn.getContactId(),
                checkIn.getOwnerId(),
                checkIn.getCheckedInAt(),
                checkIn.getStatus(),
                checkIn.getCheckedOutAt(),
                checkIn.getMethod(),
                checkIn.getNotes(),
                checkIn.getCreatedAt(),
                checkIn.getUpdatedAt());
    }
}
