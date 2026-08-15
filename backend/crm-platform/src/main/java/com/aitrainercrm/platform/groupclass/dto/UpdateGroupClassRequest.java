package com.aitrainercrm.platform.groupclass.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpdateGroupClassRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description,
        UUID defaultInstructorId,
        @NotNull @Min(5) Integer durationMinutes,
        @Min(1) Integer capacity,
        @Size(max = 200) String location,
        boolean active) {
}
