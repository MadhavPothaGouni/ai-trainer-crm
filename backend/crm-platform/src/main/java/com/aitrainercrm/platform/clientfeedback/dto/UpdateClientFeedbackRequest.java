package com.aitrainercrm.platform.clientfeedback.dto;

import com.aitrainercrm.platform.clientfeedback.entity.ClientFeedback;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record UpdateClientFeedbackRequest(
        @NotNull @Min(0) @Max(10) Integer npsScore,
        @NotNull ClientFeedback.RelatedType relatedType,
        @NotNull Instant submittedAt,
        @Size(max = 2000) String comments) {
}
