package com.aitrainercrm.platform.approval.controller;

import com.aitrainercrm.platform.approval.dto.ApprovalRequestDto;
import com.aitrainercrm.platform.approval.dto.ApprovalTaskDto;
import com.aitrainercrm.platform.approval.dto.CreateApprovalRequestRequest;
import com.aitrainercrm.platform.approval.dto.DecideStepRequest;
import com.aitrainercrm.platform.approval.entity.ApprovalRequest;
import com.aitrainercrm.platform.approval.service.ApprovalRequestService;
import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /my-approvals} is deliberately gated on the {@code APPROVE} ladder rather than
 * {@code READ} - it's a worklist of steps assigned to the caller, not a general browse of
 * approval requests, so the permission that should unlock it is "can this person approve things
 * at all," which is exactly what {@code APPROVAL_REQUEST:APPROVE:*} already means. Every other
 * endpoint follows the same {@code RESOURCE:ACTION:SCOPE} ladder convention as
 * AttachmentController; see ApprovalRequestService's javadoc for how the named-approver carve-out
 * interacts with the {@code READ} check on {@code GET /{id}}.
 */
@RestController
@RequestMapping("/api/v1/approval-requests")
@RequiredArgsConstructor
public class ApprovalRequestController {

    private final ApprovalRequestService approvalRequestService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('APPROVAL_REQUEST:READ:OWN','APPROVAL_REQUEST:READ:TEAM','APPROVAL_REQUEST:READ:DEPARTMENT','APPROVAL_REQUEST:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<ApprovalRequestDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<ApprovalRequest> page = approvalRequestService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(ApprovalRequestDto::from).toList()));
    }

    @GetMapping("/my-approvals")
    @PreAuthorize("hasAnyAuthority('APPROVAL_REQUEST:APPROVE:OWN','APPROVAL_REQUEST:APPROVE:TEAM','APPROVAL_REQUEST:APPROVE:DEPARTMENT','APPROVAL_REQUEST:APPROVE:ORGANIZATION')")
    public ApiResponse<PageResponse<ApprovalTaskDto>> myApprovals(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<ApprovalTaskDto> page = approvalRequestService.myApprovalTasks(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent()));
    }

    @GetMapping("/{requestId}")
    @PreAuthorize("hasAnyAuthority('APPROVAL_REQUEST:READ:OWN','APPROVAL_REQUEST:READ:TEAM','APPROVAL_REQUEST:READ:DEPARTMENT','APPROVAL_REQUEST:READ:ORGANIZATION')")
    public ApiResponse<ApprovalRequestDto> get(@PathVariable UUID requestId, @AuthenticationPrincipal UserPrincipal principal) {
        ApprovalRequest request = approvalRequestService.get(principal, requestId);
        return ApiResponse.ok(ApprovalRequestDto.withSteps(request, approvalRequestService.getSteps(requestId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('APPROVAL_REQUEST:CREATE:OWN','APPROVAL_REQUEST:CREATE:TEAM','APPROVAL_REQUEST:CREATE:DEPARTMENT','APPROVAL_REQUEST:CREATE:ORGANIZATION')")
    public ApiResponse<ApprovalRequestDto> create(
            @Valid @RequestBody CreateApprovalRequestRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        ApprovalRequest created = approvalRequestService.create(principal, request);
        return ApiResponse.ok(
                ApprovalRequestDto.withSteps(created, approvalRequestService.getSteps(created.getId())), "Approval request submitted");
    }

    @PostMapping("/{requestId}/steps/{stepNumber}/approve")
    @PreAuthorize("hasAnyAuthority('APPROVAL_REQUEST:APPROVE:OWN','APPROVAL_REQUEST:APPROVE:TEAM','APPROVAL_REQUEST:APPROVE:DEPARTMENT','APPROVAL_REQUEST:APPROVE:ORGANIZATION')")
    public ApiResponse<ApprovalRequestDto> approveStep(
            @PathVariable UUID requestId,
            @PathVariable int stepNumber,
            @Valid @RequestBody DecideStepRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        ApprovalRequest updated = approvalRequestService.approveStep(principal, requestId, stepNumber, request.comment());
        return ApiResponse.ok(ApprovalRequestDto.withSteps(updated, approvalRequestService.getSteps(requestId)), "Step approved");
    }

    @PostMapping("/{requestId}/steps/{stepNumber}/reject")
    @PreAuthorize("hasAnyAuthority('APPROVAL_REQUEST:APPROVE:OWN','APPROVAL_REQUEST:APPROVE:TEAM','APPROVAL_REQUEST:APPROVE:DEPARTMENT','APPROVAL_REQUEST:APPROVE:ORGANIZATION')")
    public ApiResponse<ApprovalRequestDto> rejectStep(
            @PathVariable UUID requestId,
            @PathVariable int stepNumber,
            @Valid @RequestBody DecideStepRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        ApprovalRequest updated = approvalRequestService.rejectStep(principal, requestId, stepNumber, request.comment());
        return ApiResponse.ok(ApprovalRequestDto.withSteps(updated, approvalRequestService.getSteps(requestId)), "Step rejected");
    }

    @PatchMapping("/{requestId}/cancel")
    @PreAuthorize("hasAnyAuthority('APPROVAL_REQUEST:UPDATE:OWN','APPROVAL_REQUEST:UPDATE:TEAM','APPROVAL_REQUEST:UPDATE:DEPARTMENT','APPROVAL_REQUEST:UPDATE:ORGANIZATION')")
    public ApiResponse<ApprovalRequestDto> cancel(@PathVariable UUID requestId, @AuthenticationPrincipal UserPrincipal principal) {
        ApprovalRequest cancelled = approvalRequestService.cancel(principal, requestId);
        return ApiResponse.ok(ApprovalRequestDto.withSteps(cancelled, approvalRequestService.getSteps(requestId)), "Approval request cancelled");
    }
}
