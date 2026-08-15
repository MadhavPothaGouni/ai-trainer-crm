package com.aitrainercrm.platform.payment.dto;

import com.aitrainercrm.platform.payment.entity.RefundRecord;
import jakarta.validation.constraints.NotNull;

public record UpdateRefundRecordStatusRequest(@NotNull RefundRecord.Status status) {
}
