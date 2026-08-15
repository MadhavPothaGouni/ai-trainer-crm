package com.aitrainercrm.platform.compensation.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateCompensationRecordRequest(
        @NotNull UUID staffUserId,
        @NotNull LocalDate payPeriodStart,
        @NotNull LocalDate payPeriodEnd,
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal hoursWorked,
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal hourlyRate,
        @DecimalMin(value = "0", inclusive = true) BigDecimal commissionAmount,
        @DecimalMin(value = "0", inclusive = true) BigDecimal bonusAmount,
        @Size(max = 2000) String notes,

        /** Null defaults to the creator - see AccountService#resolveOwner for the identical rule applied here. */
        UUID ownerId) {
}
