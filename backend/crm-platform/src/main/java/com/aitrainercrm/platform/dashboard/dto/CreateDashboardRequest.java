package com.aitrainercrm.platform.dashboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** {@code ownerId}: null defaults to the creator - see DashboardService#resolveOwner, same pattern ContactService/WorkflowService use. */
public record CreateDashboardRequest(@NotBlank @Size(max = 200) String name, @Size(max = 2000) String description, UUID ownerId) {
}
