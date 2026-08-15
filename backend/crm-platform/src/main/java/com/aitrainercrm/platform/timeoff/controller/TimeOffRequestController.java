package com.aitrainercrm.platform.timeoff.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.timeoff.dto.CreateTimeOffRequestRequest;
import com.aitrainercrm.platform.timeoff.dto.TimeOffRequestDto;
import com.aitrainercrm.platform.timeoff.dto.UpdateTimeOffRequestRequest;
import com.aitrainercrm.platform.timeoff.dto.UpdateTimeOffRequestStatusRequest;
import com.aitrainercrm.platform.timeoff.entity.TimeOffRequest;
import com.aitrainercrm.platform.timeoff.service.TimeOffRequestService;
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

/** Mirrors ClientGoalController/ReferralController's shape exactly, including the separate PATCH .../status endpoint for approvedAt stamping. */
@RestController
@RequestMapping("/api/v1/time-off-requests")
@RequiredArgsConstructor
public class TimeOffRequestController {

    private final TimeOffRequestService timeOffRequestService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('TIME_OFF_REQUEST:READ:OWN','TIME_OFF_REQUEST:READ:TEAM','TIME_OFF_REQUEST:READ:DEPARTMENT','TIME_OFF_REQUEST:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<TimeOffRequestDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<TimeOffRequest> page = timeOffRequestService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(TimeOffRequestDto::from).toList()));
    }

    @GetMapping("/{timeOffRequestId}")
    @PreAuthorize("hasAnyAuthority('TIME_OFF_REQUEST:READ:OWN','TIME_OFF_REQUEST:READ:TEAM','TIME_OFF_REQUEST:READ:DEPARTMENT','TIME_OFF_REQUEST:READ:ORGANIZATION')")
    public ApiResponse<TimeOffRequestDto> get(@PathVariable UUID timeOffRequestId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(TimeOffRequestDto.from(timeOffRequestService.get(principal, timeOffRequestId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('TIME_OFF_REQUEST:CREATE:OWN','TIME_OFF_REQUEST:CREATE:TEAM','TIME_OFF_REQUEST:CREATE:DEPARTMENT','TIME_OFF_REQUEST:CREATE:ORGANIZATION')")
    public ApiResponse<TimeOffRequestDto> create(
            @Valid @RequestBody CreateTimeOffRequestRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(TimeOffRequestDto.from(timeOffRequestService.create(principal, request)), "Time-off request created");
    }

    @PutMapping("/{timeOffRequestId}")
    @PreAuthorize("hasAnyAuthority('TIME_OFF_REQUEST:UPDATE:OWN','TIME_OFF_REQUEST:UPDATE:TEAM','TIME_OFF_REQUEST:UPDATE:DEPARTMENT','TIME_OFF_REQUEST:UPDATE:ORGANIZATION')")
    public ApiResponse<TimeOffRequestDto> update(
            @PathVariable UUID timeOffRequestId,
            @Valid @RequestBody UpdateTimeOffRequestRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(TimeOffRequestDto.from(timeOffRequestService.update(principal, timeOffRequestId, request)), "Time-off request updated");
    }

    @PatchMapping("/{timeOffRequestId}/status")
    @PreAuthorize("hasAnyAuthority('TIME_OFF_REQUEST:UPDATE:OWN','TIME_OFF_REQUEST:UPDATE:TEAM','TIME_OFF_REQUEST:UPDATE:DEPARTMENT','TIME_OFF_REQUEST:UPDATE:ORGANIZATION')")
    public ApiResponse<TimeOffRequestDto> updateStatus(
            @PathVariable UUID timeOffRequestId,
            @Valid @RequestBody UpdateTimeOffRequestStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(
                TimeOffRequestDto.from(timeOffRequestService.updateStatus(principal, timeOffRequestId, request.status())), "Status updated");
    }

    @DeleteMapping("/{timeOffRequestId}")
    @PreAuthorize("hasAnyAuthority('TIME_OFF_REQUEST:DELETE:OWN','TIME_OFF_REQUEST:DELETE:TEAM','TIME_OFF_REQUEST:DELETE:DEPARTMENT','TIME_OFF_REQUEST:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID timeOffRequestId, @AuthenticationPrincipal UserPrincipal principal) {
        timeOffRequestService.delete(principal, timeOffRequestId);
        return ApiResponse.ok(null, "Time-off request deleted");
    }
}
