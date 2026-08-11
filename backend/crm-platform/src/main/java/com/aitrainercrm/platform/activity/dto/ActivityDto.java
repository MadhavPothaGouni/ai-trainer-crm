package com.aitrainercrm.platform.activity.dto;

import com.aitrainercrm.platform.activity.entity.Activity;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record ActivityDto(
        UUID id,
        Activity.Type type,
        String subject,
        String description,
        Activity.Status status,
        Activity.Priority priority,
        Instant dueAt,
        Instant completedAt,
        Activity.RelatedToType relatedToType,
        UUID relatedToId,
        UUID ownerId,
        Instant createdAt,
        Instant updatedAt) {

    public static ActivityDto from(Activity activity) {
        return ActivityDto.builder()
                .id(activity.getId())
                .type(activity.getType())
                .subject(activity.getSubject())
                .description(activity.getDescription())
                .status(activity.getStatus())
                .priority(activity.getPriority())
                .dueAt(activity.getDueAt())
                .completedAt(activity.getCompletedAt())
                .relatedToType(activity.getRelatedToType())
                .relatedToId(activity.getRelatedToId())
                .ownerId(activity.getOwnerId())
                .createdAt(activity.getCreatedAt())
                .updatedAt(activity.getUpdatedAt())
                .build();
    }
}
