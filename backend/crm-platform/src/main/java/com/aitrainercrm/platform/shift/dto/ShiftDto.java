package com.aitrainercrm.platform.shift.dto;

import com.aitrainercrm.platform.shift.entity.Shift;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ShiftDto(
        UUID id,
        UUID shiftTemplateId,
        UUID ownerId,
        LocalDate shiftDate,
        Instant startsAt,
        Instant endsAt,
        Shift.Status status,
        Instant clockInAt,
        Instant clockOutAt,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static ShiftDto from(Shift shift) {
        return new ShiftDto(
                shift.getId(),
                shift.getShiftTemplateId(),
                shift.getOwnerId(),
                shift.getShiftDate(),
                shift.getStartsAt(),
                shift.getEndsAt(),
                shift.getStatus(),
                shift.getClockInAt(),
                shift.getClockOutAt(),
                shift.getNotes(),
                shift.getCreatedAt(),
                shift.getUpdatedAt());
    }
}
