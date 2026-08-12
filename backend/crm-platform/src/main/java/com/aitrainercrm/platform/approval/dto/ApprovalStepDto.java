package com.aitrainercrm.platform.approval.dto;

import com.aitrainercrm.platform.approval.entity.ApprovalRequest;
import com.aitrainercrm.platform.approval.entity.ApprovalStep;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record ApprovalStepDto(
        UUID id,
        int stepNumber,
        UUID approverUserId,
        ApprovalStep.Status status,
        String comment,
        Instant decidedAt,
        /** True when this is the request's current step and the request is still PENDING - the only state in which POST .../approve or .../reject will actually succeed for this step. */
        boolean actionable) {

    public static ApprovalStepDto from(ApprovalStep step, ApprovalRequest request) {
        return ApprovalStepDto.builder()
                .id(step.getId())
                .stepNumber(step.getStepNumber())
                .approverUserId(step.getApproverUserId())
                .status(step.getStatus())
                .comment(step.getComment())
                .decidedAt(step.getDecidedAt())
                .actionable(request.isPending() && step.getStepNumber() == request.getCurrentStepNumber())
                .build();
    }
}
