package com.aitrainercrm.platform.opportunity.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Stage is deliberately not editable here - see UpdateOpportunityStageRequest / PATCH .../stage, which also maintains actualCloseDate. */
public record UpdateOpportunityRequest(
        @NotNull UUID accountId,
        UUID primaryContactId,
        @NotBlank @Size(max = 200) String name,
        @DecimalMin(value = "0", inclusive = true) BigDecimal amount,
        @Size(min = 3, max = 3) String currency,
        LocalDate expectedCloseDate,
        @Size(max = 2000) String description) {
}
