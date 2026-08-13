package com.aitrainercrm.platform.course.dto;

import com.aitrainercrm.platform.course.entity.Course;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record CourseDto(
        UUID id,
        String title,
        String description,
        Course.Category category,
        int durationMinutes,
        int passingScorePercent,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static CourseDto from(Course course) {
        return CourseDto.builder()
                .id(course.getId())
                .title(course.getTitle())
                .description(course.getDescription())
                .category(course.getCategory())
                .durationMinutes(course.getDurationMinutes())
                .passingScorePercent(course.getPassingScorePercent())
                .active(course.isActive())
                .createdAt(course.getCreatedAt())
                .updatedAt(course.getUpdatedAt())
                .build();
    }
}
