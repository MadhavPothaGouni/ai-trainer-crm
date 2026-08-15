package com.aitrainercrm.platform.clientfeedback.dto;

import com.aitrainercrm.platform.clientfeedback.entity.ClientFeedback;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record CreateClientFeedbackRequest(
        @NotNull UUID contactId,
        @NotNull @Min(0) @Max(10) Integer npsScore,
        @NotNull ClientFeedback.RelatedType relatedType,
        @NotNull Instant submittedAt,
        @Size(max = 2000) String comments,

        /** Null defaults to the creator - see AccountService#resolveOwner for the identical rule applied here. */
        UUID ownerId) {
}
