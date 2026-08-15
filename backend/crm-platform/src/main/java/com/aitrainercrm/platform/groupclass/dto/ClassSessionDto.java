package com.aitrainercrm.platform.groupclass.dto;

import com.aitrainercrm.platform.groupclass.entity.ClassSession;
import java.time.Instant;
import java.util.UUID;

public record ClassSessionDto(
        UUID id,
        UUID groupClassId,
        UUID ownerId,
        Instant startsAt,
        Instant endsAt,
        Integer capacityOverride,
        ClassSession.Status status,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static ClassSessionDto from(ClassSession session) {
        return new ClassSessionDto(
                session.getId(),
                session.getGroupClassId(),
                session.getOwnerId(),
                session.getStartsAt(),
                session.getEndsAt(),
                session.getCapacityOverride(),
                session.getStatus(),
                session.getNotes(),
                session.getCreatedAt(),
                session.getUpdatedAt());
    }
}
