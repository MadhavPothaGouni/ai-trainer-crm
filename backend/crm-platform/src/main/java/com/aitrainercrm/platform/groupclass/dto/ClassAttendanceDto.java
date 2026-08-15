package com.aitrainercrm.platform.groupclass.dto;

import com.aitrainercrm.platform.groupclass.entity.ClassAttendance;
import java.time.Instant;
import java.util.UUID;

public record ClassAttendanceDto(
        UUID id,
        UUID classSessionId,
        UUID contactId,
        UUID ownerId,
        ClassAttendance.Status status,
        Instant registeredAt,
        Instant checkedInAt,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static ClassAttendanceDto from(ClassAttendance attendance) {
        return new ClassAttendanceDto(
                attendance.getId(),
                attendance.getClassSessionId(),
                attendance.getContactId(),
                attendance.getOwnerId(),
                attendance.getStatus(),
                attendance.getRegisteredAt(),
                attendance.getCheckedInAt(),
                attendance.getNotes(),
                attendance.getCreatedAt(),
                attendance.getUpdatedAt());
    }
}
