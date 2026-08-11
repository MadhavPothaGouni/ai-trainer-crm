package com.aitrainercrm.platform.workflow.dto;

import com.aitrainercrm.platform.workflow.entity.Workflow;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** {@code triggerResource}/{@code triggerEvent} are immutable after creation - same "set once" reasoning as CustomField's apiName/target this session. {@code ownerId}: null defaults to the creator - see WorkflowService#resolveOwner. */
public record CreateWorkflowRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @NotNull Workflow.TriggerResource triggerResource,
        @NotNull Workflow.TriggerEvent triggerEvent,
        @NotBlank @Size(max = 200) String taskSubject,
        UUID taskAssigneeUserId,
        UUID ownerId) {
}
