package com.aitrainercrm.platform.locker.dto;

import com.aitrainercrm.platform.locker.entity.Locker;
import java.time.Instant;
import java.util.UUID;

public record LockerDto(
        UUID id,
        String label,
        String location,
        Locker.Size size,
        Locker.Status status,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static LockerDto from(Locker locker) {
        return new LockerDto(
                locker.getId(),
                locker.getLabel(),
                locker.getLocation(),
                locker.getSize(),
                locker.getStatus(),
                locker.getNotes(),
                locker.getCreatedAt(),
                locker.getUpdatedAt());
    }
}
