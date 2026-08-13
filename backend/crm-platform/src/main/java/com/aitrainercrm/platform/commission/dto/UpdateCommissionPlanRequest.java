package com.aitrainercrm.platform.commission.dto;

import com.aitrainercrm.platform.commission.entity.CommissionPlan;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record UpdateCommissionPlanRequest(
        @NotBlank @Size(max = 150) String name,
        UUID ownerUserId,
        UUID teamId,
        @NotNull CommissionPlan.RateType rateType,
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal rate,
        boolean active) {
}
