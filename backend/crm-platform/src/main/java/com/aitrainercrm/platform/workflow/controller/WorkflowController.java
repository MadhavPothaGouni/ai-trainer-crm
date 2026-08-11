package com.aitrainercrm.platform.workflow.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.workflow.dto.CreateWorkflowRequest;
import com.aitrainercrm.platform.workflow.dto.RunWorkflowRequest;
import com.aitrainercrm.platform.workflow.dto.SetWorkflowActiveRequest;
import com.aitrainercrm.platform.workflow.dto.UpdateWorkflowRequest;
import com.aitrainercrm.platform.workflow.dto.WorkflowDto;
import com.aitrainercrm.platform.workflow.dto.WorkflowRunDto;
import com.aitrainercrm.platform.workflow.entity.Workflow;
import com.aitrainercrm.platform.workflow.entity.WorkflowRun;
import com.aitrainercrm.platform.workflow.service.WorkflowService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * WORKFLOW was seeded in V2 at OWN/TEAM/ORGANIZATION scope (no DEPARTMENT -
 * see V2's own comment) with CREATE/READ/UPDATE/DELETE/MANAGE - MANAGE
 * gates the two operations that go beyond plain field edits: flipping
 * {@code active} on/off and manually firing a run, the same
 * "sign-off/consequential-action gets its own permission" reasoning
 * ORDER:APPROVE and INVOICE:APPROVE use, applied to whichever action the
 * seeded catalog actually gives this resource.
 */
@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('WORKFLOW:READ:OWN','WORKFLOW:READ:TEAM','WORKFLOW:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<WorkflowDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<Workflow> page = workflowService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(WorkflowDto::from).toList()));
    }

    @GetMapping("/{workflowId}")
    @PreAuthorize("hasAnyAuthority('WORKFLOW:READ:OWN','WORKFLOW:READ:TEAM','WORKFLOW:READ:ORGANIZATION')")
    public ApiResponse<WorkflowDto> get(@PathVariable UUID workflowId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(WorkflowDto.from(workflowService.get(principal, workflowId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('WORKFLOW:CREATE:OWN','WORKFLOW:CREATE:TEAM','WORKFLOW:CREATE:ORGANIZATION')")
    public ApiResponse<WorkflowDto> create(@Valid @RequestBody CreateWorkflowRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(WorkflowDto.from(workflowService.create(principal, request)), "Workflow created");
    }

    @PutMapping("/{workflowId}")
    @PreAuthorize("hasAnyAuthority('WORKFLOW:UPDATE:OWN','WORKFLOW:UPDATE:TEAM','WORKFLOW:UPDATE:ORGANIZATION')")
    public ApiResponse<WorkflowDto> update(
            @PathVariable UUID workflowId, @Valid @RequestBody UpdateWorkflowRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(WorkflowDto.from(workflowService.update(principal, workflowId, request)), "Workflow updated");
    }

    @PatchMapping("/{workflowId}/active")
    @PreAuthorize("hasAnyAuthority('WORKFLOW:MANAGE:OWN','WORKFLOW:MANAGE:TEAM','WORKFLOW:MANAGE:ORGANIZATION')")
    public ApiResponse<WorkflowDto> setActive(
            @PathVariable UUID workflowId, @Valid @RequestBody SetWorkflowActiveRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        WorkflowDto dto = WorkflowDto.from(workflowService.setActive(principal, workflowId, request.active()));
        return ApiResponse.ok(dto, dto.active() ? "Workflow activated" : "Workflow deactivated");
    }

    @PostMapping("/{workflowId}/run")
    @PreAuthorize("hasAnyAuthority('WORKFLOW:MANAGE:OWN','WORKFLOW:MANAGE:TEAM','WORKFLOW:MANAGE:ORGANIZATION')")
    public ApiResponse<WorkflowDto> run(
            @PathVariable UUID workflowId, @Valid @RequestBody RunWorkflowRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(
                WorkflowDto.from(workflowService.runManually(principal, workflowId, request.resourceId())), "Workflow run");
    }

    @DeleteMapping("/{workflowId}")
    @PreAuthorize("hasAnyAuthority('WORKFLOW:DELETE:OWN','WORKFLOW:DELETE:TEAM','WORKFLOW:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID workflowId, @AuthenticationPrincipal UserPrincipal principal) {
        workflowService.delete(principal, workflowId);
        return ApiResponse.ok(null, "Workflow deleted");
    }

    @GetMapping("/{workflowId}/runs")
    @PreAuthorize("hasAnyAuthority('WORKFLOW:READ:OWN','WORKFLOW:READ:TEAM','WORKFLOW:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<WorkflowRunDto>> listRuns(
            @PathVariable UUID workflowId, Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<WorkflowRun> page = workflowService.listRuns(principal, workflowId, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(WorkflowRunDto::from).toList()));
    }
}
