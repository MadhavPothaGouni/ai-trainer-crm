package com.aitrainercrm.platform.groupclass.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.groupclass.dto.ClassAttendanceDto;
import com.aitrainercrm.platform.groupclass.dto.CreateClassAttendanceRequest;
import com.aitrainercrm.platform.groupclass.dto.UpdateClassAttendanceRequest;
import com.aitrainercrm.platform.groupclass.dto.UpdateClassAttendanceStatusRequest;
import com.aitrainercrm.platform.groupclass.entity.ClassAttendance;
import com.aitrainercrm.platform.groupclass.service.ClassAttendanceService;
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

/** Mirrors ClassSessionController's shape - see ClassAttendanceService's javadoc for the capacity-check business rule create() enforces. */
@RestController
@RequestMapping("/api/v1/class-attendances")
@RequiredArgsConstructor
public class ClassAttendanceController {

    private final ClassAttendanceService classAttendanceService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('CLASS_ATTENDANCE:READ:OWN','CLASS_ATTENDANCE:READ:TEAM','CLASS_ATTENDANCE:READ:DEPARTMENT','CLASS_ATTENDANCE:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<ClassAttendanceDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<ClassAttendance> page = classAttendanceService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(ClassAttendanceDto::from).toList()));
    }

    @GetMapping("/{classAttendanceId}")
    @PreAuthorize("hasAnyAuthority('CLASS_ATTENDANCE:READ:OWN','CLASS_ATTENDANCE:READ:TEAM','CLASS_ATTENDANCE:READ:DEPARTMENT','CLASS_ATTENDANCE:READ:ORGANIZATION')")
    public ApiResponse<ClassAttendanceDto> get(@PathVariable UUID classAttendanceId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ClassAttendanceDto.from(classAttendanceService.get(principal, classAttendanceId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('CLASS_ATTENDANCE:CREATE:OWN','CLASS_ATTENDANCE:CREATE:TEAM','CLASS_ATTENDANCE:CREATE:DEPARTMENT','CLASS_ATTENDANCE:CREATE:ORGANIZATION')")
    public ApiResponse<ClassAttendanceDto> create(
            @Valid @RequestBody CreateClassAttendanceRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ClassAttendanceDto.from(classAttendanceService.create(principal, request)), "Attendance registered");
    }

    @PutMapping("/{classAttendanceId}")
    @PreAuthorize("hasAnyAuthority('CLASS_ATTENDANCE:UPDATE:OWN','CLASS_ATTENDANCE:UPDATE:TEAM','CLASS_ATTENDANCE:UPDATE:DEPARTMENT','CLASS_ATTENDANCE:UPDATE:ORGANIZATION')")
    public ApiResponse<ClassAttendanceDto> update(
            @PathVariable UUID classAttendanceId,
            @Valid @RequestBody UpdateClassAttendanceRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ClassAttendanceDto.from(classAttendanceService.update(principal, classAttendanceId, request)), "Attendance updated");
    }

    @PatchMapping("/{classAttendanceId}/status")
    @PreAuthorize("hasAnyAuthority('CLASS_ATTENDANCE:UPDATE:OWN','CLASS_ATTENDANCE:UPDATE:TEAM','CLASS_ATTENDANCE:UPDATE:DEPARTMENT','CLASS_ATTENDANCE:UPDATE:ORGANIZATION')")
    public ApiResponse<ClassAttendanceDto> updateStatus(
            @PathVariable UUID classAttendanceId,
            @Valid @RequestBody UpdateClassAttendanceStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ClassAttendanceDto.from(classAttendanceService.updateStatus(principal, classAttendanceId, request.status())), "Status updated");
    }

    @DeleteMapping("/{classAttendanceId}")
    @PreAuthorize("hasAnyAuthority('CLASS_ATTENDANCE:DELETE:OWN','CLASS_ATTENDANCE:DELETE:TEAM','CLASS_ATTENDANCE:DELETE:DEPARTMENT','CLASS_ATTENDANCE:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID classAttendanceId, @AuthenticationPrincipal UserPrincipal principal) {
        classAttendanceService.delete(principal, classAttendanceId);
        return ApiResponse.ok(null, "Attendance deleted");
    }
}
