package com.aitrainercrm.platform.webhook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateWebhookSubscriptionRequest(
        @NotBlank @Size(max = 500) String url, @Size(max = 100) String eventType, @NotNull Boolean active) {}
