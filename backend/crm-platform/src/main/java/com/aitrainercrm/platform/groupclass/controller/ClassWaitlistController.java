package com.aitrainercrm.platform.groupclass.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.groupclass.dto.ClassWaitlistDto;
import com.aitrainercrm.platform.groupclass.dto.CreateClassWaitlistRequest;
import com.aitrainercrm.platform.groupclass.dto.UpdateClassWaitlistRequest;
import com.aitrainercrm.platform.groupclass.dto.UpdateClassWaitlistStatusRequest;
import com.aitrainercrm.platform.groupclass.entity.ClassWaitlist;
import com.aitrainercrm.platform.groupclass.service.ClassWaitlistService;
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
@RequestMapping("/api/v1/class-waitlists")
@RequiredArgsConstructor
public class ClassWaitlistController {

    private final ClassWaitlistService classWaitlistService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('CLASS_WAITLIST:READ:OWN','CLASS_WAITLIST:READ:TEAM','CLASS_WAITLIST:READ:DEPARTMENT','CLASS_WAITLIST:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<ClassWaitlistDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<ClassWaitlist> page = classWaitlistService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(ClassWaitlistDto::from).toList()));
    }

    @GetMapping("/{classWaitlistId}")
    @PreAuthorize("hasAnyAuthority('CLASS_WAITLIST:READ:OWN','CLASS_WAITLIST:READ:TEAM','CLASS_WAITLIST:READ:DEPARTMENT','CLASS_WAITLIST:READ:ORGANIZATION')")
    public ApiResponse<ClassWaitlistDto> get(@PathVariable UUID classWaitlistId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ClassWaitlistDto.from(classWaitlistService.get(principal, classWaitlistId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('CLASS_WAITLIST:CREATE:OWN','CLASS_WAITLIST:CREATE:TEAM','CLASS_WAITLIST:CREATE:DEPARTMENT','CLASS_WAITLIST:CREATE:ORGANIZATION')")
    public ApiResponse<ClassWaitlistDto> create(
            @Valid @RequestBody CreateClassWaitlistRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ClassWaitlistDto.from(classWaitlistService.create(principal, request)), "Added to waitlist");
    }

    @PutMapping("/{classWaitlistId}")
    @PreAuthorize("hasAnyAuthority('CLASS_WAITLIST:UPDATE:OWN','CLASS_WAITLIST:UPDATE:TEAM','CLASS_WAITLIST:UPDATE:DEPARTMENT','CLASS_WAITLIST:UPDATE:ORGANIZATION')")
    public ApiResponse<ClassWaitlistDto> update(
            @PathVariable UUID classWaitlistId,
            @Valid @RequestBody UpdateClassWaitlistRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ClassWaitlistDto.from(classWaitlistService.update(principal, classWaitlistId, request)), "Waitlist entry updated");
    }

    @PatchMapping("/{classWaitlistId}/status")
    @PreAuthorize("hasAnyAuthority('CLASS_WAITLIST:UPDATE:OWN','CLASS_WAITLIST:UPDATE:TEAM','CLASS_WAITLIST:UPDATE:DEPARTMENT','CLASS_WAITLIST:UPDATE:ORGANIZATION')")
    public ApiResponse<ClassWaitlistDto> updateStatus(
            @PathVariable UUID classWaitlistId,
            @Valid @RequestBody UpdateClassWaitlistStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(
                ClassWaitlistDto.from(classWaitlistService.updateStatus(principal, classWaitlistId, request.status())), "Waitlist status updated");
    }

    @DeleteMapping("/{classWaitlistId}")
    @PreAuthorize("hasAnyAuthority('CLASS_WAITLIST:DELETE:OWN','CLASS_WAITLIST:DELETE:TEAM','CLASS_WAITLIST:DELETE:DEPARTMENT','CLASS_WAITLIST:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID classWaitlistId, @AuthenticationPrincipal UserPrincipal principal) {
        classWaitlistService.delete(principal, classWaitlistId);
        return ApiResponse.ok(null, "Waitlist entry removed");
    }
}
