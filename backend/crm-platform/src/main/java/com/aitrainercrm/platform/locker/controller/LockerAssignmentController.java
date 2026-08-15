package com.aitrainercrm.platform.locker.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.locker.dto.CreateLockerAssignmentRequest;
import com.aitrainercrm.platform.locker.dto.LockerAssignmentDto;
import com.aitrainercrm.platform.locker.dto.UpdateLockerAssignmentRequest;
import com.aitrainercrm.platform.locker.dto.UpdateLockerAssignmentStatusRequest;
import com.aitrainercrm.platform.locker.entity.LockerAssignment;
import com.aitrainercrm.platform.locker.service.LockerAssignmentService;
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

/** Mirrors PurchaseOrderController's shape exactly, including the separate PATCH .../status endpoint for returnedAt stamping. */
@RestController
@RequestMapping("/api/v1/locker-assignments")
@RequiredArgsConstructor
public class LockerAssignmentController {

    private final LockerAssignmentService lockerAssignmentService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('LOCKER_ASSIGNMENT:READ:OWN','LOCKER_ASSIGNMENT:READ:TEAM','LOCKER_ASSIGNMENT:READ:DEPARTMENT','LOCKER_ASSIGNMENT:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<LockerAssignmentDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<LockerAssignment> page = lockerAssignmentService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(LockerAssignmentDto::from).toList()));
    }

    @GetMapping("/{lockerAssignmentId}")
    @PreAuthorize("hasAnyAuthority('LOCKER_ASSIGNMENT:READ:OWN','LOCKER_ASSIGNMENT:READ:TEAM','LOCKER_ASSIGNMENT:READ:DEPARTMENT','LOCKER_ASSIGNMENT:READ:ORGANIZATION')")
    public ApiResponse<LockerAssignmentDto> get(@PathVariable UUID lockerAssignmentId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(LockerAssignmentDto.from(lockerAssignmentService.get(principal, lockerAssignmentId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('LOCKER_ASSIGNMENT:CREATE:OWN','LOCKER_ASSIGNMENT:CREATE:TEAM','LOCKER_ASSIGNMENT:CREATE:DEPARTMENT','LOCKER_ASSIGNMENT:CREATE:ORGANIZATION')")
    public ApiResponse<LockerAssignmentDto> create(
            @Valid @RequestBody CreateLockerAssignmentRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(LockerAssignmentDto.from(lockerAssignmentService.create(principal, request)), "Locker assignment created");
    }

    @PutMapping("/{lockerAssignmentId}")
    @PreAuthorize("hasAnyAuthority('LOCKER_ASSIGNMENT:UPDATE:OWN','LOCKER_ASSIGNMENT:UPDATE:TEAM','LOCKER_ASSIGNMENT:UPDATE:DEPARTMENT','LOCKER_ASSIGNMENT:UPDATE:ORGANIZATION')")
    public ApiResponse<LockerAssignmentDto> update(
            @PathVariable UUID lockerAssignmentId,
            @Valid @RequestBody UpdateLockerAssignmentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(LockerAssignmentDto.from(lockerAssignmentService.update(principal, lockerAssignmentId, request)), "Locker assignment updated");
    }

    @PatchMapping("/{lockerAssignmentId}/status")
    @PreAuthorize("hasAnyAuthority('LOCKER_ASSIGNMENT:UPDATE:OWN','LOCKER_ASSIGNMENT:UPDATE:TEAM','LOCKER_ASSIGNMENT:UPDATE:DEPARTMENT','LOCKER_ASSIGNMENT:UPDATE:ORGANIZATION')")
    public ApiResponse<LockerAssignmentDto> updateStatus(
            @PathVariable UUID lockerAssignmentId,
            @Valid @RequestBody UpdateLockerAssignmentStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(
                LockerAssignmentDto.from(lockerAssignmentService.updateStatus(principal, lockerAssignmentId, request.status())), "Status updated");
    }

    @DeleteMapping("/{lockerAssignmentId}")
    @PreAuthorize("hasAnyAuthority('LOCKER_ASSIGNMENT:DELETE:OWN','LOCKER_ASSIGNMENT:DELETE:TEAM','LOCKER_ASSIGNMENT:DELETE:DEPARTMENT','LOCKER_ASSIGNMENT:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID lockerAssignmentId, @AuthenticationPrincipal UserPrincipal principal) {
        lockerAssignmentService.delete(principal, lockerAssignmentId);
        return ApiResponse.ok(null, "Locker assignment deleted");
    }
}
