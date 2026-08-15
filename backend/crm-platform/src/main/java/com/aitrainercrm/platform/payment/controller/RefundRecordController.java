package com.aitrainercrm.platform.payment.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.payment.dto.CreateRefundRecordRequest;
import com.aitrainercrm.platform.payment.dto.RefundRecordDto;
import com.aitrainercrm.platform.payment.dto.UpdateRefundRecordRequest;
import com.aitrainercrm.platform.payment.dto.UpdateRefundRecordStatusRequest;
import com.aitrainercrm.platform.payment.entity.RefundRecord;
import com.aitrainercrm.platform.payment.service.RefundRecordService;
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

/** Standard CRUD plus a PATCH .../status endpoint - mirrors CompensationRecordController's shape. */
@RestController
@RequestMapping("/api/v1/refund-records")
@RequiredArgsConstructor
public class RefundRecordController {

    private final RefundRecordService refundRecordService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('REFUND_RECORD:READ:OWN','REFUND_RECORD:READ:TEAM','REFUND_RECORD:READ:DEPARTMENT','REFUND_RECORD:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<RefundRecordDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<RefundRecord> page = refundRecordService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(RefundRecordDto::from).toList()));
    }

    @GetMapping("/{refundRecordId}")
    @PreAuthorize("hasAnyAuthority('REFUND_RECORD:READ:OWN','REFUND_RECORD:READ:TEAM','REFUND_RECORD:READ:DEPARTMENT','REFUND_RECORD:READ:ORGANIZATION')")
    public ApiResponse<RefundRecordDto> get(@PathVariable UUID refundRecordId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(RefundRecordDto.from(refundRecordService.get(principal, refundRecordId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('REFUND_RECORD:CREATE:OWN','REFUND_RECORD:CREATE:TEAM','REFUND_RECORD:CREATE:DEPARTMENT','REFUND_RECORD:CREATE:ORGANIZATION')")
    public ApiResponse<RefundRecordDto> create(
            @Valid @RequestBody CreateRefundRecordRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(RefundRecordDto.from(refundRecordService.create(principal, request)), "Refund requested");
    }

    @PutMapping("/{refundRecordId}")
    @PreAuthorize("hasAnyAuthority('REFUND_RECORD:UPDATE:OWN','REFUND_RECORD:UPDATE:TEAM','REFUND_RECORD:UPDATE:DEPARTMENT','REFUND_RECORD:UPDATE:ORGANIZATION')")
    public ApiResponse<RefundRecordDto> update(
            @PathVariable UUID refundRecordId,
            @Valid @RequestBody UpdateRefundRecordRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(RefundRecordDto.from(refundRecordService.update(principal, refundRecordId, request)), "Refund updated");
    }

    @PatchMapping("/{refundRecordId}/status")
    @PreAuthorize("hasAnyAuthority('REFUND_RECORD:UPDATE:OWN','REFUND_RECORD:UPDATE:TEAM','REFUND_RECORD:UPDATE:DEPARTMENT','REFUND_RECORD:UPDATE:ORGANIZATION')")
    public ApiResponse<RefundRecordDto> updateStatus(
            @PathVariable UUID refundRecordId,
            @Valid @RequestBody UpdateRefundRecordStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(
                RefundRecordDto.from(refundRecordService.updateStatus(principal, refundRecordId, request.status())), "Refund status updated");
    }

    @DeleteMapping("/{refundRecordId}")
    @PreAuthorize("hasAnyAuthority('REFUND_RECORD:DELETE:OWN','REFUND_RECORD:DELETE:TEAM','REFUND_RECORD:DELETE:DEPARTMENT','REFUND_RECORD:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID refundRecordId, @AuthenticationPrincipal UserPrincipal principal) {
        refundRecordService.delete(principal, refundRecordId);
        return ApiResponse.ok(null, "Refund deleted");
    }
}
