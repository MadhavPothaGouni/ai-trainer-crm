package com.aitrainercrm.platform.report.dto;

import com.aitrainercrm.platform.opportunity.entity.Opportunity;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Internal projection used only by {@code OpportunityAnalyticsRepository} and
 * {@code ReportService#repLeaderboard} to build the per-rep leaderboard - one
 * row per (owner, stage) combination. Never returned directly by
 * {@code ReportController}; the service buckets these into
 * {@link RepLeaderboardEntryDto} (won/open/lost per owner, with a resolved
 * display name) before responding.
 */
public record OwnerStageAggregateDto(UUID ownerId, Opportunity.Stage stage, Long opportunityCount, BigDecimal totalAmount) {}
