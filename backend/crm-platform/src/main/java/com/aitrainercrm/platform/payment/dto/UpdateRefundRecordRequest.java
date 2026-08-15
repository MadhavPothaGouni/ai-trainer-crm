package com.aitrainercrm.platform.payment.dto;

import com.aitrainercrm.platform.payment.entity.RefundRecord;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateRefundRecordRequest(
        @NotNull @Positive BigDecimal amount, @NotNull RefundRecord.Reason reason, @Size(max = 2000) String notes) {
}
