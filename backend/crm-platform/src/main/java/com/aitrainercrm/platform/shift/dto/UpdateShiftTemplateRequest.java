package com.aitrainercrm.platform.shift.dto;

import com.aitrainercrm.platform.shift.entity.ShiftTemplate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

public record UpdateShiftTemplateRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull ShiftTemplate.DayOfWeek dayOfWeek,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @Size(max = 100) String role,
        boolean active) {
}
