package com.aitrainercrm.platform.contract.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateContractRequest(
        @NotNull UUID accountId,

        /** Null is fine - a renewal contract has no opportunity behind it. Non-null must exist in the same organization. See Contract's javadoc. */
        UUID opportunityId,

        @NotBlank @Size(max = 50) String contractNumber,
        @NotBlank @Size(max = 200) String title,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @DecimalMin(value = "0", inclusive = true) BigDecimal totalValue,
        boolean autoRenew,

        /** Only meaningful when autoRenew is true - see Contract's javadoc. */
        @Min(1) Integer renewalTermMonths,

        @Size(max = 4000) String terms,

        /** Null defaults to the creator - see AccountService#resolveOwner for the identical rule applied here. */
        UUID ownerId) {
}
