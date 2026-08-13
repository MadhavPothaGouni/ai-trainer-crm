package com.aitrainercrm.platform.sequence.dto;

import com.aitrainercrm.platform.sequence.entity.SequenceEnrollment;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record SequenceEnrollmentDto(
        UUID id,
        UUID sequenceId,
        SequenceEnrollment.TargetType targetType,
        UUID targetId,
        UUID ownerId,
        int currentStepIndex,
        SequenceEnrollment.Status status,
        Instant enrolledAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static SequenceEnrollmentDto from(SequenceEnrollment enrollment) {
        return SequenceEnrollmentDto.builder()
                .id(enrollment.getId())
                .sequenceId(enrollment.getSequenceId())
                .targetType(enrollment.getTargetType())
                .targetId(enrollment.getTargetId())
                .ownerId(enrollment.getOwnerId())
                .currentStepIndex(enrollment.getCurrentStepIndex())
                .status(enrollment.getStatus())
                .enrolledAt(enrollment.getEnrolledAt())
                .completedAt(enrollment.getCompletedAt())
                .createdAt(enrollment.getCreatedAt())
                .updatedAt(enrollment.getUpdatedAt())
                .build();
    }
}
