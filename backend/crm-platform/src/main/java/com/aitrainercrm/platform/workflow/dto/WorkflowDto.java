package com.aitrainercrm.platform.workflow.dto;

import com.aitrainercrm.platform.workflow.entity.Workflow;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record WorkflowDto(
        UUID id,
        UUID ownerId,
        String name,
        String description,
        Workflow.TriggerResource triggerResource,
        Workflow.TriggerEvent triggerEvent,
        Workflow.ActionType actionType,
        String taskSubject,
        UUID taskAssigneeUserId,
        boolean active,
        int runCount,
        Instant lastRunAt,
        Instant createdAt) {

    public static WorkflowDto from(Workflow workflow) {
        return WorkflowDto.builder()
                .id(workflow.getId())
                .ownerId(workflow.getOwnerId())
                .name(workflow.getName())
                .description(workflow.getDescription())
                .triggerResource(workflow.getTriggerResource())
                .triggerEvent(workflow.getTriggerEvent())
                .actionType(workflow.getActionType())
                .taskSubject(workflow.getTaskSubject())
                .taskAssigneeUserId(workflow.getTaskAssigneeUserId())
                .active(workflow.isActive())
                .runCount(workflow.getRunCount())
                .lastRunAt(workflow.getLastRunAt())
                .createdAt(workflow.getCreatedAt())
                .build();
    }
}
