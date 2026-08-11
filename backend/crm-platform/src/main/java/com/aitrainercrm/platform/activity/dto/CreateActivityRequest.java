package com.aitrainercrm.platform.activity.dto;

import com.aitrainercrm.platform.activity.entity.Activity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record CreateActivityRequest(
        @NotNull Activity.Type type,
        @NotBlank @Size(max = 200) String subject,
        @Size(max = 2000) String description,
        Activity.Priority priority,
        Instant dueAt,
        @NotNull Activity.RelatedToType relatedToType,
        @NotNull UUID relatedToId,

        /** Null defaults to the creator - same convention as CreateAccountRequest.ownerId (see ActivityService#resolveOwner). */
        UUID ownerId) {
}
