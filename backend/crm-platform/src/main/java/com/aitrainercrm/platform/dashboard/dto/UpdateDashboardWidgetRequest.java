package com.aitrainercrm.platform.dashboard.dto;

import jakarta.validation.constraints.Size;

/** {@code reportType} is immutable after creation - same "set once" reasoning CustomField's apiName/target and Workflow's trigger fields already established this session; changing what a widget shows is deleting it and adding a new one. */
public record UpdateDashboardWidgetRequest(@Size(max = 200) String title, int displayOrder, int width, int height) {
}
