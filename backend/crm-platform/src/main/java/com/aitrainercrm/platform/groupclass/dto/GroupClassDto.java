package com.aitrainercrm.platform.groupclass.dto;

import com.aitrainercrm.platform.groupclass.entity.GroupClass;
import java.time.Instant;
import java.util.UUID;

public record GroupClassDto(
        UUID id,
        String name,
        String description,
        UUID defaultInstructorId,
        int durationMinutes,
        Integer capacity,
        String location,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static GroupClassDto from(GroupClass groupClass) {
        return new GroupClassDto(
                groupClass.getId(),
                groupClass.getName(),
                groupClass.getDescription(),
                groupClass.getDefaultInstructorId(),
                groupClass.getDurationMinutes(),
                groupClass.getCapacity(),
                groupClass.getLocation(),
                groupClass.isActive(),
                groupClass.getCreatedAt(),
                groupClass.getUpdatedAt());
    }
}
