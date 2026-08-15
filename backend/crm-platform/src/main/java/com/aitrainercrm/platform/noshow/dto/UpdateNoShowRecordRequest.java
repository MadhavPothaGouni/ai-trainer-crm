package com.aitrainercrm.platform.noshow.dto;

import com.aitrainercrm.platform.noshow.entity.NoShowRecord;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record UpdateNoShowRecordRequest(
        @NotNull Instant occurredAt,
        @NotNull NoShowRecord.RelatedType relatedType,
        @DecimalMin(value = "0", inclusive = true) BigDecimal feeAmount,
        @Size(max = 2000) String notes) {
}
