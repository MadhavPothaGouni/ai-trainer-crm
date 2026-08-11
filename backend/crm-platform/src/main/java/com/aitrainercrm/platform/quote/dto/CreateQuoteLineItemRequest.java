package com.aitrainercrm.platform.quote.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * productId is optional (a one-off/custom line needs none), but description
 * and unitPrice are always required from the caller - rather than the
 * service defaulting them from the referenced product when omitted, which
 * would need conditional validation Bean Validation can't express cleanly.
 * The frontend prefills both from the selected product; the caller can still
 * override either before saving.
 */
public record CreateQuoteLineItemRequest(
        UUID productId,
        @NotBlank @Size(max = 500) String description,
        @Min(1) int quantity,
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal unitPrice) {
}
