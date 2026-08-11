package com.aitrainercrm.platform.workflow.dto;

import com.aitrainercrm.platform.workflow.entity.WorkflowRun;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record WorkflowRunDto(
        UUID id, UUID resourceId, UUID createdActivityId, WorkflowRun.Status status, String errorMessage, Instant ranAt) {

    public static WorkflowRunDto from(WorkflowRun run) {
        return WorkflowRunDto.builder()
                .id(run.getId())
                .resourceId(run.getResourceId())
                .createdActivityId(run.getCreatedActivityId())
                .status(run.getStatus())
                .errorMessage(run.getErrorMessage())
                .ranAt(run.getCreatedAt())
                .build();
    }
}
