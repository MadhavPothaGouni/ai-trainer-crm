package com.aitrainercrm.platform.dashboard.dto;

import com.aitrainercrm.platform.dashboard.entity.DashboardWidget;
import java.util.UUID;
import lombok.Builder;

/**
 * A widget's definition plus its live data, {@code data}'s actual runtime
 * shape depending on {@code reportType} - {@code List<PipelineStageSummaryDto>}
 * for PIPELINE_BY_STAGE, {@code List<LeadFunnelStageDto>} for LEAD_FUNNEL,
 * {@code List<RepLeaderboardEntryDto>} for LEADERBOARD. Declared as
 * {@code Object} rather than a sealed/generic type deliberately - Jackson
 * serializes whichever concrete list {@code DashboardService#widgetData}
 * put there just fine, and the frontend already knows which shape to
 * expect from {@code reportType} (see {@code types/api.ts}'s discriminated-
 * union-by-reportType handling), so a shared wire envelope wins over
 * three near-identical DTOs.
 */
@Builder
public record DashboardWidgetDataDto(
        UUID id, DashboardWidget.ReportType reportType, String title, int displayOrder, int width, int height, Object data) {
}
