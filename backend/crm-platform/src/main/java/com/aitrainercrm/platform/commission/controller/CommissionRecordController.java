package com.aitrainercrm.platform.commission.controller;

import com.aitrainercrm.platform.commission.dto.CommissionRecordDto;
import com.aitrainercrm.platform.commission.dto.UpdateCommissionRecordStatusRequest;
import com.aitrainercrm.platform.commission.entity.CommissionRecord;
import com.aitrainercrm.platform.commission.service.CommissionRecordService;
import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** No CREATE/UPDATE/DELETE anywhere here - CommissionEngine is the only writer of a record's core
 * fields, and the one mutation an admin can make (walking status forward) is APPROVE, not UPDATE.
 * {@link #mine} needs no permission at all - the fourth-kind, notification-style self-scoped shape
 * this codebase reuses repeatedly rather than reinventing per module. */
@RestController
@RequestMapping("/api/v1/commission-records")
@RequiredArgsConstructor
public class CommissionRecordController {

    private final CommissionRecordService commissionRecordService;

    @GetMapping
    @PreAuthorize("hasAuthority('COMMISSION_RECORD:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<CommissionRecordDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<CommissionRecord> page = commissionRecordService.list(principal.getOrganizationId(), pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(CommissionRecordDto::from).toList()));
    }

    @GetMapping("/mine")
    public ApiResponse<List<CommissionRecordDto>> mine(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(commissionRecordService.myRecords(principal).stream().map(CommissionRecordDto::from).toList());
    }

    @GetMapping("/{recordId}")
    @PreAuthorize("hasAuthority('COMMISSION_RECORD:READ:ORGANIZATION')")
    public ApiResponse<CommissionRecordDto> get(@PathVariable UUID recordId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(CommissionRecordDto.from(commissionRecordService.get(principal.getOrganizationId(), recordId)));
    }

    @PatchMapping("/{recordId}/status")
    @PreAuthorize("hasAuthority('COMMISSION_RECORD:APPROVE:ORGANIZATION')")
    public ApiResponse<CommissionRecordDto> updateStatus(
            @PathVariable UUID recordId, @Valid @RequestBody UpdateCommissionRecordStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(
                CommissionRecordDto.from(commissionRecordService.updateStatus(principal.getOrganizationId(), recordId, request)),
                "Commission record updated");
    }
}
