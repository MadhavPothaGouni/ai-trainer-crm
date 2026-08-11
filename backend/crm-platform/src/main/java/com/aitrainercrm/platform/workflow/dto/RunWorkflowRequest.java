package com.aitrainercrm.platform.workflow.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Manually fires a workflow against a specific record, as if its trigger event had just happened - useful for testing a workflow's configuration without waiting for a real Lead/Contact/Account/Opportunity change. */
public record RunWorkflowRequest(@NotNull UUID resourceId) {
}
