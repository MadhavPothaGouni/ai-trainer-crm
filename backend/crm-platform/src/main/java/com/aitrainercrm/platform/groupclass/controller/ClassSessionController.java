package com.aitrainercrm.platform.groupclass.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.groupclass.dto.ClassSessionDto;
import com.aitrainercrm.platform.groupclass.dto.CreateClassSessionRequest;
import com.aitrainercrm.platform.groupclass.dto.UpdateClassSessionRequest;
import com.aitrainercrm.platform.groupclass.dto.UpdateClassSessionStatusRequest;
import com.aitrainercrm.platform.groupclass.entity.ClassSession;
import com.aitrainercrm.platform.groupclass.service.ClassSessionService;
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

/** Mirrors MembershipController's shape exactly, including the separate PATCH .../status endpoint. */
@RestController
@RequestMapping("/api/v1/class-sessions")
@RequiredArgsConstructor
public class ClassSessionController {

    private final ClassSessionService classSessionService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('CLASS_SESSION:READ:OWN','CLASS_SESSION:READ:TEAM','CLASS_SESSION:READ:DEPARTMENT','CLASS_SESSION:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<ClassSessionDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<ClassSession> page = classSessionService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(ClassSessionDto::from).toList()));
    }

    @GetMapping("/{classSessionId}")
    @PreAuthorize("hasAnyAuthority('CLASS_SESSION:READ:OWN','CLASS_SESSION:READ:TEAM','CLASS_SESSION:READ:DEPARTMENT','CLASS_SESSION:READ:ORGANIZATION')")
    public ApiResponse<ClassSessionDto> get(@PathVariable UUID classSessionId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ClassSessionDto.from(classSessionService.get(principal, classSessionId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('CLASS_SESSION:CREATE:OWN','CLASS_SESSION:CREATE:TEAM','CLASS_SESSION:CREATE:DEPARTMENT','CLASS_SESSION:CREATE:ORGANIZATION')")
    public ApiResponse<ClassSessionDto> create(@Valid @RequestBody CreateClassSessionRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ClassSessionDto.from(classSessionService.create(principal, request)), "Class session scheduled");
    }

    @PutMapping("/{classSessionId}")
    @PreAuthorize("hasAnyAuthority('CLASS_SESSION:UPDATE:OWN','CLASS_SESSION:UPDATE:TEAM','CLASS_SESSION:UPDATE:DEPARTMENT','CLASS_SESSION:UPDATE:ORGANIZATION')")
    public ApiResponse<ClassSessionDto> update(
            @PathVariable UUID classSessionId, @Valid @RequestBody UpdateClassSessionRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ClassSessionDto.from(classSessionService.update(principal, classSessionId, request)), "Class session updated");
    }

    @PatchMapping("/{classSessionId}/status")
    @PreAuthorize("hasAnyAuthority('CLASS_SESSION:UPDATE:OWN','CLASS_SESSION:UPDATE:TEAM','CLASS_SESSION:UPDATE:DEPARTMENT','CLASS_SESSION:UPDATE:ORGANIZATION')")
    public ApiResponse<ClassSessionDto> updateStatus(
            @PathVariable UUID classSessionId, @Valid @RequestBody UpdateClassSessionStatusRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ClassSessionDto.from(classSessionService.updateStatus(principal, classSessionId, request.status())), "Status updated");
    }

    @DeleteMapping("/{classSessionId}")
    @PreAuthorize("hasAnyAuthority('CLASS_SESSION:DELETE:OWN','CLASS_SESSION:DELETE:TEAM','CLASS_SESSION:DELETE:DEPARTMENT','CLASS_SESSION:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID classSessionId, @AuthenticationPrincipal UserPrincipal principal) {
        classSessionService.delete(principal, classSessionId);
        return ApiResponse.ok(null, "Class session deleted");
    }
}
