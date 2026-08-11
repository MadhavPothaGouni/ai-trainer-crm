package com.aitrainercrm.platform.dashboard.dto;

import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
public record DashboardDataDto(UUID dashboardId, String name, List<DashboardWidgetDataDto> widgets) {
}
