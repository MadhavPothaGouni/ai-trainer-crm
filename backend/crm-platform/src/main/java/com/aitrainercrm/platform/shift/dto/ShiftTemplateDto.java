package com.aitrainercrm.platform.shift.dto;

import com.aitrainercrm.platform.shift.entity.ShiftTemplate;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

public record ShiftTemplateDto(
        UUID id,
        String name,
        ShiftTemplate.DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        String role,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static ShiftTemplateDto from(ShiftTemplate template) {
        return new ShiftTemplateDto(
                template.getId(),
                template.getName(),
                template.getDayOfWeek(),
                template.getStartTime(),
                template.getEndTime(),
                template.getRole(),
                template.isActive(),
                template.getCreatedAt(),
                template.getUpdatedAt());
    }
}
