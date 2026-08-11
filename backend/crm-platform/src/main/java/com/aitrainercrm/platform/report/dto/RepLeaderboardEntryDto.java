package com.aitrainercrm.platform.report.dto;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;

/**
 * One row of the rep leaderboard: a single opportunity owner's open
 * pipeline and closed-won/closed-lost totals. Sorted by {@code wonAmount}
 * descending in {@code ReportService#repLeaderboard} - reps with zero won
 * deals still appear (sorted to the bottom) as long as they own at least
 * one non-deleted opportunity, so the leaderboard doubles as "who's
 * actually working the pipeline," not just "who's closed something."
 */
@Builder
public record RepLeaderboardEntryDto(
        UUID ownerId,
        String ownerName,
        long openCount,
        BigDecimal openAmount,
        long wonCount,
        BigDecimal wonAmount,
        long lostCount) {}
