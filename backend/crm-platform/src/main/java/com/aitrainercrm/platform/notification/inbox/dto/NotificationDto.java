package com.aitrainercrm.platform.notification.inbox.dto;

import com.aitrainercrm.platform.notification.inbox.entity.Notification;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record NotificationDto(
        UUID id,
        UUID senderUserId,
        Notification.Type type,
        String title,
        String body,
        Notification.RelatedToType relatedToType,
        UUID relatedToId,
        Instant readAt,
        Instant createdAt) {

    public static NotificationDto from(Notification notification) {
        return NotificationDto.builder()
                .id(notification.getId())
                .senderUserId(notification.getSenderUserId())
                .type(notification.getType())
                .title(notification.getTitle())
                .body(notification.getBody())
                .relatedToType(notification.getRelatedToType())
                .relatedToId(notification.getRelatedToId())
                .readAt(notification.getReadAt())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
