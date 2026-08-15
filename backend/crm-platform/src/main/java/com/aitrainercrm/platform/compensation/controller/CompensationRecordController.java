package com.aitrainercrm.platform.compensation.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.compensation.dto.CompensationRecordDto;
import com.aitrainercrm.platform.compensation.dto.CreateCompensationRecordRequest;
import com.aitrainercrm.platform.compensation.dto.UpdateCompensationRecordRequest;
import com.aitrainercrm.platform.compensation.dto.UpdateCompensationRecordStatusRequest;
import com.aitrainercrm.platform.compensation.entity.CompensationRecord;
import com.aitrainercrm.platform.compensation.service.CompensationRecordService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
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

/** Mirrors LockerAssignmentController's shape, including the separate PATCH .../status endpoint for paidAt stamping. */
@RestController
@RequestMapping("/api/v1/compensation-records")
@RequiredArgsConstructor
public class CompensationRecordController {

    private final CompensationRecordService compensationRecordService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('COMPENSATION_RECORD:READ:OWN','COMPENSATION_RECORD:READ:TEAM','COMPENSATION_RECORD:READ:DEPARTMENT','COMPENSATION_RECORD:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<CompensationRecordDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<CompensationRecord> page = compensationRecordService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(CompensationRecordDto::from).toList()));
    }

    @GetMapping("/{compensationRecordId}")
    @PreAuthorize("hasAnyAuthority('COMPENSATION_RECORD:READ:OWN','COMPENSATION_RECORD:READ:TEAM','COMPENSATION_RECORD:READ:DEPARTMENT','COMPENSATION_RECORD:READ:ORGANIZATION')")
    public ApiResponse<CompensationRecordDto> get(@PathVariable UUID compensationRecordId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(CompensationRecordDto.from(compensationRecordService.get(principal, compensationRecordId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('COMPENSATION_RECORD:CREATE:OWN','COMPENSATION_RECORD:CREATE:TEAM','COMPENSATION_RECORD:CREATE:DEPARTMENT','COMPENSATION_RECORD:CREATE:ORGANIZATION')")
    public ApiResponse<CompensationRecordDto> create(
            @Valid @RequestBody CreateCompensationRecordRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(CompensationRecordDto.from(compensationRecordService.create(principal, request)), "Compensation record created");
    }

    @PutMapping("/{compensationRecordId}")
    @PreAuthorize("hasAnyAuthority('COMPENSATION_RECORD:UPDATE:OWN','COMPENSATION_RECORD:UPDATE:TEAM','COMPENSATION_RECORD:UPDATE:DEPARTMENT','COMPENSATION_RECORD:UPDATE:ORGANIZATION')")
    public ApiResponse<CompensationRecordDto> update(
            @PathVariable UUID compensationRecordId,
            @Valid @RequestBody UpdateCompensationRecordRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(
                CompensationRecordDto.from(compensationRecordService.update(principal, compensationRecordId, request)), "Compensation record updated");
    }

    @PatchMapping("/{compensationRecordId}/status")
    @PreAuthorize("hasAnyAuthority('COMPENSATION_RECORD:UPDATE:OWN','COMPENSATION_RECORD:UPDATE:TEAM','COMPENSATION_RECORD:UPDATE:DEPARTMENT','COMPENSATION_RECORD:UPDATE:ORGANIZATION')")
    public ApiResponse<CompensationRecordDto> updateStatus(
            @PathVariable UUID compensationRecordId,
            @Valid @RequestBody UpdateCompensationRecordStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(
                CompensationRecordDto.from(compensationRecordService.updateStatus(principal, compensationRecordId, request.status())), "Status updated");
    }

    @DeleteMapping("/{compensationRecordId}")
    @PreAuthorize("hasAnyAuthority('COMPENSATION_RECORD:DELETE:OWN','COMPENSATION_RECORD:DELETE:TEAM','COMPENSATION_RECORD:DELETE:DEPARTMENT','COMPENSATION_RECORD:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID compensationRecordId, @AuthenticationPrincipal UserPrincipal principal) {
        compensationRecordService.delete(principal, compensationRecordId);
        return ApiResponse.ok(null, "Compensation record deleted");
    }
}
