package com.aitrainercrm.platform.commission.dto;

import com.aitrainercrm.platform.commission.entity.CommissionPlan;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/** Exactly one of ownerUserId/teamId must be set - CommissionPlanService#assertExactlyOneTarget
 * re-validates this even though chk_commission_plans_exactly_one_target (V29) already would, the
 * same defense-in-depth SalesGoalService documents for its own identical check. */
public record CreateCommissionPlanRequest(
        @NotBlank @Size(max = 150) String name,
        UUID ownerUserId,
        UUID teamId,
        @NotNull CommissionPlan.RateType rateType,
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal rate) {
}
