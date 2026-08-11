package com.aitrainercrm.platform.dashboard.dto;

import com.aitrainercrm.platform.dashboard.entity.DashboardWidget;
import java.util.UUID;
import lombok.Builder;

@Builder
public record DashboardWidgetDto(
        UUID id, DashboardWidget.ReportType reportType, String title, int displayOrder, int width, int height) {

    public static DashboardWidgetDto from(DashboardWidget widget) {
        return DashboardWidgetDto.builder()
                .id(widget.getId())
                .reportType(widget.getReportType())
                .title(widget.getTitle() != null ? widget.getTitle() : defaultTitle(widget.getReportType()))
                .displayOrder(widget.getDisplayOrder())
                .width(widget.getWidth())
                .height(widget.getHeight())
                .build();
    }

    private static String defaultTitle(DashboardWidget.ReportType reportType) {
        return switch (reportType) {
            case PIPELINE_BY_STAGE -> "Pipeline by stage";
            case LEAD_FUNNEL -> "Lead conversion funnel";
            case LEADERBOARD -> "Rep leaderboard";
        };
    }
}
