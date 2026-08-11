package com.aitrainercrm.platform.webhook.dto;

import com.aitrainercrm.platform.webhook.entity.WebhookSubscription;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/**
 * Unlike {@code ApiKeyDto}, {@code secret} is always present here, not just
 * on creation - see V7's migration comment for why a webhook signing
 * secret has to stay readable forever (the subscriber needs it to verify
 * every delivery's signature) while an API key secret never does.
 */
@Builder
public record WebhookSubscriptionDto(
        UUID id,
        String url,
        String eventType,
        String secret,
        boolean active,
        UUID createdByUserId,
        Instant lastTriggeredAt,
        Integer lastResponseStatus,
        Instant createdAt,
        Instant updatedAt) {

    public static WebhookSubscriptionDto from(WebhookSubscription subscription) {
        return WebhookSubscriptionDto.builder()
                .id(subscription.getId())
                .url(subscription.getUrl())
                .eventType(subscription.getEventType())
                .secret(subscription.getSecret())
                .active(subscription.isActive())
                .createdByUserId(subscription.getCreatedByUserId())
                .lastTriggeredAt(subscription.getLastTriggeredAt())
                .lastResponseStatus(subscription.getLastResponseStatus())
                .createdAt(subscription.getCreatedAt())
                .updatedAt(subscription.getUpdatedAt())
                .build();
    }
}
