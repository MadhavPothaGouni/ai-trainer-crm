package com.aitrainercrm.platform.quote.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** No opportunityId (immutable after creation) and no ownerId/status (their own endpoints) - see Quote's javadoc for why. */
public record UpdateQuoteRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 3) String currency,
        LocalDate validUntil,
        @DecimalMin(value = "0", inclusive = true) BigDecimal discountAmount,
        @DecimalMin(value = "0", inclusive = true) BigDecimal taxAmount) {
}
