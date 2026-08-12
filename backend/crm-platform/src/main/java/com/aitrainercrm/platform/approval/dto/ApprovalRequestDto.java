package com.aitrainercrm.platform.approval.dto;

import com.aitrainercrm.platform.approval.entity.ApprovalRequest;
import com.aitrainercrm.platform.approval.entity.ApprovalStep;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
public record ApprovalRequestDto(
        UUID id,
        ApprovalRequest.RelatedToType relatedToType,
        UUID relatedToId,
        UUID requestedByUserId,
        String title,
        ApprovalRequest.Status status,
        int currentStepNumber,
        Instant decidedAt,
        Instant createdAt,
        List<ApprovalStepDto> steps) {

    /** List endpoints use this - steps omitted, same reasoning QuoteDto's list view doesn't eagerly load line items either; the detail page fetches them via #withSteps. */
    public static ApprovalRequestDto from(ApprovalRequest request) {
        return builderFor(request).steps(List.of()).build();
    }

    public static ApprovalRequestDto withSteps(ApprovalRequest request, List<ApprovalStep> steps) {
        return builderFor(request)
                .steps(steps.stream().map(step -> ApprovalStepDto.from(step, request)).toList())
                .build();
    }

    private static ApprovalRequestDtoBuilder builderFor(ApprovalRequest request) {
        return ApprovalRequestDto.builder()
                .id(request.getId())
                .relatedToType(request.getRelatedToType())
                .relatedToId(request.getRelatedToId())
                .requestedByUserId(request.getRequestedByUserId())
                .title(request.getTitle())
                .status(request.getStatus())
                .currentStepNumber(request.getCurrentStepNumber())
                .decidedAt(request.getDecidedAt())
                .createdAt(request.getCreatedAt());
    }
}
