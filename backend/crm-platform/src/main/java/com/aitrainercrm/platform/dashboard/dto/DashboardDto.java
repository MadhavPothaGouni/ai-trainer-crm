package com.aitrainercrm.platform.dashboard.dto;

import com.aitrainercrm.platform.dashboard.entity.Dashboard;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
public record DashboardDto(
        UUID id, UUID ownerId, String name, String description, boolean isDefault, Instant createdAt, List<DashboardWidgetDto> widgets) {

    public static DashboardDto from(Dashboard dashboard, List<DashboardWidgetDto> widgets) {
        return DashboardDto.builder()
                .id(dashboard.getId())
                .ownerId(dashboard.getOwnerId())
                .name(dashboard.getName())
                .description(dashboard.getDescription())
                .isDefault(dashboard.isDefaultDashboard())
                .createdAt(dashboard.getCreatedAt())
                .widgets(widgets)
                .build();
    }
}
