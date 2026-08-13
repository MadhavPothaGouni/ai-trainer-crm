package com.aitrainercrm.platform.contract.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Status is deliberately not editable here - see UpdateContractStatusRequest / PATCH .../status, same reasoning UpdateTicketRequest documents. */
public record UpdateContractRequest(
        UUID opportunityId,
        @NotBlank @Size(max = 50) String contractNumber,
        @NotBlank @Size(max = 200) String title,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @DecimalMin(value = "0", inclusive = true) BigDecimal totalValue,
        boolean autoRenew,
        @Min(1) Integer renewalTermMonths,
        @Size(max = 4000) String terms) {
}
