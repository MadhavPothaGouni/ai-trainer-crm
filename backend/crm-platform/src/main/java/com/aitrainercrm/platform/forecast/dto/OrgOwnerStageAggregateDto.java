package com.aitrainercrm.platform.forecast.dto;

import com.aitrainercrm.platform.opportunity.entity.Opportunity;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Internal projection used only by {@code PipelineCaptureRepository} and {@code
 * PipelineSnapshotService#capture} - one row per (organization, owner, stage) combination across
 * every organization on the platform, never returned directly by {@code
 * PipelineSnapshotController}. See {@code report.dto.OwnerStageAggregateDto}, which this
 * deliberately mirrors one field further (adding {@code organizationId}) - that DTO's query is
 * already scoped to a single organization by the caller's own tenant, but capturing snapshots for
 * every tenant in one pass, the same way {@code SlaEvaluationService#sweep} evaluates every
 * organization's tickets in one query, needs the tenant boundary carried in the row itself.
 */
public record OrgOwnerStageAggregateDto(UUID organizationId, UUID ownerId, Opportunity.Stage stage, Long dealCount, BigDecimal totalValue) {}
