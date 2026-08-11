package com.aitrainercrm.platform.dashboard.dto;

import com.aitrainercrm.platform.dashboard.entity.DashboardWidget;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateDashboardWidgetRequest(
        @NotNull DashboardWidget.ReportType reportType, @Size(max = 200) String title, int displayOrder, int width, int height) {
}
