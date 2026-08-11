package com.aitrainercrm.platform.report.dto;

import com.aitrainercrm.platform.lead.entity.Lead;

/**
 * One row of the lead conversion funnel: how many leads sit at a given
 * {@link Lead.Status}. Like {@link PipelineStageSummaryDto},
 * {@link com.aitrainercrm.platform.report.service.ReportService#leadFunnel}
 * always returns one row per {@link Lead.Status} value, zero-filled.
 */
public record LeadFunnelStageDto(Lead.Status status, Long leadCount) {}
