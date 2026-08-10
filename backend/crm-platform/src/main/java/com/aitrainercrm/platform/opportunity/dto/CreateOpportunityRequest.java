package com.aitrainercrm.platform.opportunity.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateOpportunityRequest(
        @NotNull UUID accountId,
        UUID primaryContactId,
        @NotBlank @Size(max = 200) String name,
        @DecimalMin(value = "0", inclusive = true) BigDecimal amount,
        @Size(min = 3, max = 3) String currency,
        LocalDate expectedCloseDate,
        @Size(max = 2000) String description,

        /** Null defaults to the creator - see AccountService#resolveOwner for the identical rule applied here. */
        UUID ownerId) {
}
