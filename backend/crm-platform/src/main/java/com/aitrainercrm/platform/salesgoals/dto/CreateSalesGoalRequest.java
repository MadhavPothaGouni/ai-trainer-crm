package com.aitrainercrm.platform.salesgoals.dto;

import com.aitrainercrm.platform.salesgoals.entity.SalesGoal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Exactly one of ownerUserId/teamId must be set - SalesGoalService rejects both-set and neither-set, same rule CreateTerritoryRuleRequest documents for its own assignment target. */
public record CreateSalesGoalRequest(
        @NotBlank @Size(max = 150) String name,
        UUID ownerUserId,
        UUID teamId,
        @NotNull SalesGoal.Metric metric,
        @NotNull @Positive BigDecimal targetValue,
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd) {
}
