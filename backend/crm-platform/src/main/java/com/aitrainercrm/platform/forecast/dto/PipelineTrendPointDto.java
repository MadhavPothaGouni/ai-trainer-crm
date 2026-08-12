package com.aitrainercrm.platform.forecast.dto;

import com.aitrainercrm.platform.opportunity.entity.Opportunity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * One day's worth of pipeline, folded from every {@code PipelineSnapshotDto} row visible to the
 * caller on that date - {@code dealCount}/{@code totalValue} are totals across every stage and
 * every visible owner, {@code valueByStage} is the same total broken down per {@link
 * Opportunity.Stage} so a trend chart can render either a single line or a stacked-by-stage view
 * without a second request. Built by {@code PipelineSnapshotService#trend} by folding the exact
 * same rows {@code #listSnapshots} returns, the same "one query, two derived shapes" reasoning
 * {@code ReportService#repLeaderboard} already uses on {@code aggregateByOwnerAndStage}.
 */
public record PipelineTrendPointDto(LocalDate date, int dealCount, BigDecimal totalValue, Map<Opportunity.Stage, BigDecimal> valueByStage) {}
