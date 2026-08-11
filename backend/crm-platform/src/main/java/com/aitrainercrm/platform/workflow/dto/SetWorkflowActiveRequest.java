package com.aitrainercrm.platform.workflow.dto;

import jakarta.validation.constraints.NotNull;

public record SetWorkflowActiveRequest(@NotNull Boolean active) {
}
