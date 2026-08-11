package com.aitrainercrm.platform.report.dto;

import com.aitrainercrm.platform.opportunity.entity.Opportunity;
import java.math.BigDecimal;

/**
 * One row of the pipeline-by-stage report: how many open (or closed)
 * opportunities sit in a given {@link Opportunity.Stage}, and their total
 * {@code amount}. {@link com.aitrainercrm.platform.report.service.ReportService#pipelineByStage}
 * always returns one row per {@link Opportunity.Stage} value, zero-filled,
 * so a chart built from this never has to handle a missing stage.
 *
 * <p>{@code opportunityCount} is boxed {@code Long}, not primitive {@code long}
 * - Hibernate's JPQL {@code select new ...} constructor expression binds
 * {@code count(o)} to it directly, and that's a {@code Long} on the wire.
 */
public record PipelineStageSummaryDto(Opportunity.Stage stage, Long opportunityCount, BigDecimal totalAmount) {}
