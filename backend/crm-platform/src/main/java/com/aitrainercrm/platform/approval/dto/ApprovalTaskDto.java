package com.aitrainercrm.platform.approval.dto;

import com.aitrainercrm.platform.approval.entity.ApprovalRequest;
import com.aitrainercrm.platform.approval.entity.ApprovalStep;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/**
 * One row in the "my approvals" inbox (GET /approval-requests/my-approvals) - a step assigned
 * to the caller, flattened with just enough of its parent request's context (title, what it's
 * about) that the page doesn't need a second round-trip per row.
 */
@Builder
public record ApprovalTaskDto(
        UUID stepId,
        UUID approvalRequestId,
        String requestTitle,
        ApprovalRequest.RelatedToType relatedToType,
        UUID relatedToId,
        UUID requestedByUserId,
        int stepNumber,
        boolean actionable,
        Instant createdAt) {

    public static ApprovalTaskDto from(ApprovalStep step, ApprovalRequest request) {
        return ApprovalTaskDto.builder()
                .stepId(step.getId())
                .approvalRequestId(request.getId())
                .requestTitle(request.getTitle())
                .relatedToType(request.getRelatedToType())
                .relatedToId(request.getRelatedToId())
                .requestedByUserId(request.getRequestedByUserId())
                .stepNumber(step.getStepNumber())
                .actionable(request.isPending() && step.getStepNumber() == request.getCurrentStepNumber())
                .createdAt(step.getCreatedAt())
                .build();
    }
}
