package com.aitrainercrm.platform.webhook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWebhookSubscriptionRequest(
        @NotBlank @Size(max = 500) String url,

        /** Null/blank subscribes to every event type - see WebhookSubscription#matches. */
        @Size(max = 100) String eventType) {}
