package com.aitrainercrm.platform.payment.dto;

import com.aitrainercrm.platform.payment.entity.Payment;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/** invoiceId comes from the path (POST /invoices/{invoiceId}/payments), matching InvoiceController's own path-param conventions. paidAt defaults to now when omitted. */
public record CreatePaymentRequest(
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
        @NotNull Payment.Method method,
        @Size(max = 200) String reference,
        Instant paidAt,
        @Size(max = 1000) String notes) {
}
