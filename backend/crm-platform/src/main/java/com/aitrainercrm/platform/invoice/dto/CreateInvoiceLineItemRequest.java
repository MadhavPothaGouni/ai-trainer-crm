package com.aitrainercrm.platform.invoice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateInvoiceLineItemRequest(
        UUID productId,
        @NotBlank @Size(max = 500) String description,
        @Min(1) int quantity,
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal unitPrice) {
}
