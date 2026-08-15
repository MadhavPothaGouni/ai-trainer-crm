package com.aitrainercrm.platform.payment.dto;

import com.aitrainercrm.platform.payment.entity.RefundRecord;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RefundRecordDto(
        UUID id,
        UUID paymentId,
        UUID ownerId,
        BigDecimal amount,
        RefundRecord.Reason reason,
        RefundRecord.Status status,
        Instant processedAt,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static RefundRecordDto from(RefundRecord refund) {
        return new RefundRecordDto(
                refund.getId(),
                refund.getPaymentId(),
                refund.getOwnerId(),
                refund.getAmount(),
                refund.getReason(),
                refund.getStatus(),
                refund.getProcessedAt(),
                refund.getNotes(),
                refund.getCreatedAt(),
                refund.getUpdatedAt());
    }
}
