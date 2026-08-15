package com.aitrainercrm.platform.timeoff.dto;

import com.aitrainercrm.platform.timeoff.entity.TimeOffRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TimeOffRequestDto(
        UUID id,
        UUID ownerId,
        LocalDate startDate,
        LocalDate endDate,
        TimeOffRequest.Type type,
        TimeOffRequest.Status status,
        Instant approvedAt,
        String reason,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static TimeOffRequestDto from(TimeOffRequest request) {
        return new TimeOffRequestDto(
                request.getId(),
                request.getOwnerId(),
                request.getStartDate(),
                request.getEndDate(),
                request.getType(),
                request.getStatus(),
                request.getApprovedAt(),
                request.getReason(),
                request.getNotes(),
                request.getCreatedAt(),
                request.getUpdatedAt());
    }
}
