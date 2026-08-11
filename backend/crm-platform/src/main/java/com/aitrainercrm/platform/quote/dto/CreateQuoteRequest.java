package com.aitrainercrm.platform.quote.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateQuoteRequest(
        @NotNull UUID opportunityId,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 3) String currency,
        LocalDate validUntil,
        @DecimalMin(value = "0", inclusive = true) BigDecimal discountAmount,
        @DecimalMin(value = "0", inclusive = true) BigDecimal taxAmount,

        /** Null defaults to the creator - same convention as CreateAccountRequest.ownerId. */
        UUID ownerId) {
}
