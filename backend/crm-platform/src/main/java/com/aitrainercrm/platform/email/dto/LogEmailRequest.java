package com.aitrainercrm.platform.email.dto;

import com.aitrainercrm.platform.email.entity.EmailMessage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Backs both create (POST) and update (PUT) - unlike Ticket, there's no status sub-resource to split out, so one shape covers both. */
public record LogEmailRequest(
        @NotNull EmailMessage.Direction direction,
        @NotBlank @Size(max = 500) String subject,
        @Size(max = 10000) String body,
        @NotBlank @Size(max = 255) String fromAddress,
        @NotBlank @Size(max = 2000) String toAddresses,
        @Size(max = 2000) String ccAddresses,
        @NotNull EmailMessage.RelatedToType relatedToType,
        @NotNull UUID relatedToId,

        /** Null defaults to now - logging an email you just sent shouldn't require re-typing the current time, but a bulk-logged inbox import might backdate this. */
        Instant sentAt,

        /** Null defaults to the creator - see AccountService#resolveOwner for the identical rule applied here. */
        UUID ownerId) {
}
