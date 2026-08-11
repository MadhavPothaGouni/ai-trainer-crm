package com.aitrainercrm.platform.payment.dto;

import com.aitrainercrm.platform.payment.entity.Payment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record PaymentDto(
        UUID id,
        UUID invoiceId,
        BigDecimal amount,
        Payment.Method method,
        String reference,
        Instant paidAt,
        String notes,
        Instant createdAt) {

    public static PaymentDto from(Payment payment) {
        return PaymentDto.builder()
                .id(payment.getId())
                .invoiceId(payment.getInvoiceId())
                .amount(payment.getAmount())
                .method(payment.getMethod())
                .reference(payment.getReference())
                .paidAt(payment.getPaidAt())
                .notes(payment.getNotes())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
