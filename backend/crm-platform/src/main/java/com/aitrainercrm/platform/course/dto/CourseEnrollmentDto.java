package com.aitrainercrm.platform.course.dto;

import com.aitrainercrm.platform.course.entity.CourseEnrollment;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

@Builder
public record CourseEnrollmentDto(
        UUID id,
        UUID courseId,
        UUID userId,
        UUID assignedByUserId,
        CourseEnrollment.Status status,
        Integer scorePercent,
        LocalDate dueDate,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static CourseEnrollmentDto from(CourseEnrollment enrollment) {
        return CourseEnrollmentDto.builder()
                .id(enrollment.getId())
                .courseId(enrollment.getCourseId())
                .userId(enrollment.getUserId())
                .assignedByUserId(enrollment.getAssignedByUserId())
                .status(enrollment.getStatus())
                .scorePercent(enrollment.getScorePercent())
                .dueDate(enrollment.getDueDate())
                .startedAt(enrollment.getStartedAt())
                .completedAt(enrollment.getCompletedAt())
                .createdAt(enrollment.getCreatedAt())
                .updatedAt(enrollment.getUpdatedAt())
                .build();
    }
}
