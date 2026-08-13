package com.aitrainercrm.platform.course.dto;

import com.aitrainercrm.platform.course.entity.Course;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateCourseRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description,
        @NotNull Course.Category category,
        @Min(0) int durationMinutes,
        @Min(0) @Max(100) int passingScorePercent,
        boolean active) {
}
