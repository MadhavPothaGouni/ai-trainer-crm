package com.aitrainercrm.platform.certification.controller;

import com.aitrainercrm.platform.certification.dto.AwardCertificationRequest;
import com.aitrainercrm.platform.certification.dto.UpdateUserCertificationStatusRequest;
import com.aitrainercrm.platform.certification.dto.UserCertificationDto;
import com.aitrainercrm.platform.certification.entity.UserCertification;
import com.aitrainercrm.platform.certification.service.UserCertificationService;
import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Mirrors CourseEnrollmentController's shape exactly - see UserCertificationService's javadoc. */
@RestController
@RequestMapping("/api/v1/user-certifications")
@RequiredArgsConstructor
public class UserCertificationController {

    private final UserCertificationService userCertificationService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('USER_CERTIFICATION:READ:OWN','USER_CERTIFICATION:READ:TEAM','USER_CERTIFICATION:READ:DEPARTMENT','USER_CERTIFICATION:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<UserCertificationDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<UserCertification> page = userCertificationService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(UserCertificationDto::from).toList()));
    }

    @GetMapping("/{userCertificationId}")
    @PreAuthorize("hasAnyAuthority('USER_CERTIFICATION:READ:OWN','USER_CERTIFICATION:READ:TEAM','USER_CERTIFICATION:READ:DEPARTMENT','USER_CERTIFICATION:READ:ORGANIZATION')")
    public ApiResponse<UserCertificationDto> get(@PathVariable UUID userCertificationId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(UserCertificationDto.from(userCertificationService.get(principal, userCertificationId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('USER_CERTIFICATION:CREATE:OWN','USER_CERTIFICATION:CREATE:TEAM','USER_CERTIFICATION:CREATE:DEPARTMENT','USER_CERTIFICATION:CREATE:ORGANIZATION')")
    public ApiResponse<UserCertificationDto> award(
            @Valid @RequestBody AwardCertificationRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(UserCertificationDto.from(userCertificationService.award(principal, request)), "Certification recorded");
    }

    @PatchMapping("/{userCertificationId}/status")
    @PreAuthorize("hasAnyAuthority('USER_CERTIFICATION:UPDATE:OWN','USER_CERTIFICATION:UPDATE:TEAM','USER_CERTIFICATION:UPDATE:DEPARTMENT','USER_CERTIFICATION:UPDATE:ORGANIZATION')")
    public ApiResponse<UserCertificationDto> updateStatus(
            @PathVariable UUID userCertificationId, @Valid @RequestBody UpdateUserCertificationStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserCertification updated = userCertificationService.updateStatus(principal, userCertificationId, request.status(), request.notes());
        return ApiResponse.ok(UserCertificationDto.from(updated), "Status updated");
    }

    @DeleteMapping("/{userCertificationId}")
    @PreAuthorize("hasAnyAuthority('USER_CERTIFICATION:DELETE:OWN','USER_CERTIFICATION:DELETE:TEAM','USER_CERTIFICATION:DELETE:DEPARTMENT','USER_CERTIFICATION:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID userCertificationId, @AuthenticationPrincipal UserPrincipal principal) {
        userCertificationService.delete(principal, userCertificationId);
        return ApiResponse.ok(null, "Record removed");
    }
}
