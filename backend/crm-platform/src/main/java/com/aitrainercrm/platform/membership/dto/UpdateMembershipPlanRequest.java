package com.aitrainercrm.platform.membership.dto;

import com.aitrainercrm.platform.membership.entity.MembershipPlan;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateMembershipPlanRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @NotNull MembershipPlan.BillingCycle billingCycle,
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal price,
        @Size(max = 3) String currency,
        @Min(1) Integer sessionCredits,
        boolean active) {
}
