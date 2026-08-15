package com.aitrainercrm.platform.payment.dto;

import com.aitrainercrm.platform.payment.entity.RefundRecord;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateRefundRecordRequest(
        @NotNull UUID paymentId,
        @NotNull @Positive BigDecimal amount,
        @NotNull RefundRecord.Reason reason,
        @Size(max = 2000) String notes,

        /** Null defaults to the creator - see AccountService#resolveOwner for the identical rule applied here. */
        UUID ownerId) {
}
