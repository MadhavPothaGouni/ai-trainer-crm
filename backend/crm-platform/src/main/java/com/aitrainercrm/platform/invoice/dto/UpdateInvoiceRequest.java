package com.aitrainercrm.platform.invoice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Only permitted while the invoice is still DRAFT - see InvoiceService#update. */
public record UpdateInvoiceRequest(
        @NotBlank @Size(max = 50) String invoiceNumber,
        @Size(max = 3) String currency,
        @NotNull LocalDate issueDate,
        @NotNull LocalDate dueDate,
        @DecimalMin(value = "0", inclusive = true) BigDecimal discountAmount,
        @DecimalMin(value = "0", inclusive = true) BigDecimal taxAmount) {
}
