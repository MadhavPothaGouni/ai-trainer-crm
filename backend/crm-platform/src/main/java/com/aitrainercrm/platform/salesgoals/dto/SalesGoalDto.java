package com.aitrainercrm.platform.salesgoals.dto;

import com.aitrainercrm.platform.salesgoals.entity.SalesGoal;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

/**
 * {@link #actualValue}/{@link #percentComplete} are computed live by {@code SalesGoalService}
 * every time this DTO is built - never stored, never stale, see {@link SalesGoal}'s javadoc.
 * {@link #percentComplete} is not capped at 100 - exceeding a quota is a real, meaningful state
 * worth showing exactly as it is, not clamping away.
 */
@Builder
public record SalesGoalDto(
        UUID id,
        String name,
        UUID ownerUserId,
        UUID teamId,
        SalesGoal.Metric metric,
        BigDecimal targetValue,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal actualValue,
        BigDecimal percentComplete,
        Instant createdAt,
        Instant updatedAt) {
}
