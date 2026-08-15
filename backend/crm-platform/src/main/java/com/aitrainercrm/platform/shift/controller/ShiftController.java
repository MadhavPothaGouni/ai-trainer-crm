package com.aitrainercrm.platform.shift.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.shift.dto.CreateShiftRequest;
import com.aitrainercrm.platform.shift.dto.ShiftDto;
import com.aitrainercrm.platform.shift.dto.UpdateShiftRequest;
import com.aitrainercrm.platform.shift.dto.UpdateShiftStatusRequest;
import com.aitrainercrm.platform.shift.entity.Shift;
import com.aitrainercrm.platform.shift.service.ShiftService;
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

/** Mirrors MembershipController/ClassSessionController's shape exactly, including the separate PATCH .../status endpoint for clock-in/out stamping. */
@RestController
@RequestMapping("/api/v1/shifts")
@RequiredArgsConstructor
public class ShiftController {

    private final ShiftService shiftService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SHIFT:READ:OWN','SHIFT:READ:TEAM','SHIFT:READ:DEPARTMENT','SHIFT:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<ShiftDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<Shift> page = shiftService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(ShiftDto::from).toList()));
    }

    @GetMapping("/{shiftId}")
    @PreAuthorize("hasAnyAuthority('SHIFT:READ:OWN','SHIFT:READ:TEAM','SHIFT:READ:DEPARTMENT','SHIFT:READ:ORGANIZATION')")
    public ApiResponse<ShiftDto> get(@PathVariable UUID shiftId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ShiftDto.from(shiftService.get(principal, shiftId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('SHIFT:CREATE:OWN','SHIFT:CREATE:TEAM','SHIFT:CREATE:DEPARTMENT','SHIFT:CREATE:ORGANIZATION')")
    public ApiResponse<ShiftDto> create(@Valid @RequestBody CreateShiftRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ShiftDto.from(shiftService.create(principal, request)), "Shift scheduled");
    }

    @PutMapping("/{shiftId}")
    @PreAuthorize("hasAnyAuthority('SHIFT:UPDATE:OWN','SHIFT:UPDATE:TEAM','SHIFT:UPDATE:DEPARTMENT','SHIFT:UPDATE:ORGANIZATION')")
    public ApiResponse<ShiftDto> update(
            @PathVariable UUID shiftId, @Valid @RequestBody UpdateShiftRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ShiftDto.from(shiftService.update(principal, shiftId, request)), "Shift updated");
    }

    @PatchMapping("/{shiftId}/status")
    @PreAuthorize("hasAnyAuthority('SHIFT:UPDATE:OWN','SHIFT:UPDATE:TEAM','SHIFT:UPDATE:DEPARTMENT','SHIFT:UPDATE:ORGANIZATION')")
    public ApiResponse<ShiftDto> updateStatus(
            @PathVariable UUID shiftId, @Valid @RequestBody UpdateShiftStatusRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ShiftDto.from(shiftService.updateStatus(principal, shiftId, request.status())), "Status updated");
    }

    @DeleteMapping("/{shiftId}")
    @PreAuthorize("hasAnyAuthority('SHIFT:DELETE:OWN','SHIFT:DELETE:TEAM','SHIFT:DELETE:DEPARTMENT','SHIFT:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID shiftId, @AuthenticationPrincipal UserPrincipal principal) {
        shiftService.delete(principal, shiftId);
        return ApiResponse.ok(null, "Shift deleted");
    }
}
