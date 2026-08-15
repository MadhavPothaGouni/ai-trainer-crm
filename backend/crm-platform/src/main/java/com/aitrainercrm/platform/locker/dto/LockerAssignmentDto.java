package com.aitrainercrm.platform.locker.dto;

import com.aitrainercrm.platform.locker.entity.LockerAssignment;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LockerAssignmentDto(
        UUID id,
        UUID lockerId,
        UUID contactId,
        UUID ownerId,
        Instant assignedAt,
        LocalDate expiresAt,
        LockerAssignment.Status status,
        Instant returnedAt,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static LockerAssignmentDto from(LockerAssignment assignment) {
        return new LockerAssignmentDto(
                assignment.getId(),
                assignment.getLockerId(),
                assignment.getContactId(),
                assignment.getOwnerId(),
                assignment.getAssignedAt(),
                assignment.getExpiresAt(),
                assignment.getStatus(),
                assignment.getReturnedAt(),
                assignment.getNotes(),
                assignment.getCreatedAt(),
                assignment.getUpdatedAt());
    }
}
