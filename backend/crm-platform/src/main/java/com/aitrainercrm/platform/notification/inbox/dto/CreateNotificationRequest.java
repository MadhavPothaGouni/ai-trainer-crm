package com.aitrainercrm.platform.notification.inbox.dto;

import com.aitrainercrm.platform.notification.inbox.entity.Notification;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Sends a notification to a teammate - "send," not "log," unlike LogEmailRequest, since this always creates a brand new inbox item for someone else; there's no update/edit endpoint for a notification, only read/unread and delete on the recipient's own side. */
public record CreateNotificationRequest(
        @NotNull UUID recipientUserId,
        @NotNull Notification.Type type,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String body,
        Notification.RelatedToType relatedToType,
        UUID relatedToId) {
}
