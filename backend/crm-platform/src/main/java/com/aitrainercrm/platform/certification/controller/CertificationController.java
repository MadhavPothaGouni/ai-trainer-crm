package com.aitrainercrm.platform.certification.controller;

import com.aitrainercrm.platform.certification.dto.CertificationDto;
import com.aitrainercrm.platform.certification.dto.CreateCertificationRequest;
import com.aitrainercrm.platform.certification.dto.UpdateCertificationRequest;
import com.aitrainercrm.platform.certification.entity.Certification;
import com.aitrainercrm.platform.certification.service.CertificationService;
import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** No OWN scope on CERTIFICATION - mirrors CourseController exactly. */
@RestController
@RequestMapping("/api/v1/certifications")
@RequiredArgsConstructor
public class CertificationController {

    private final CertificationService certificationService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('CERTIFICATION:READ:TEAM','CERTIFICATION:READ:DEPARTMENT','CERTIFICATION:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<CertificationDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<Certification> page = certificationService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(CertificationDto::from).toList()));
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyAuthority('CERTIFICATION:READ:TEAM','CERTIFICATION:READ:DEPARTMENT','CERTIFICATION:READ:ORGANIZATION')")
    public ApiResponse<List<CertificationDto>> listActive(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(certificationService.listActive(principal).stream().map(CertificationDto::from).toList());
    }

    @GetMapping("/{certificationId}")
    @PreAuthorize("hasAnyAuthority('CERTIFICATION:READ:TEAM','CERTIFICATION:READ:DEPARTMENT','CERTIFICATION:READ:ORGANIZATION')")
    public ApiResponse<CertificationDto> get(@PathVariable UUID certificationId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(CertificationDto.from(certificationService.get(principal, certificationId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('CERTIFICATION:CREATE:TEAM','CERTIFICATION:CREATE:DEPARTMENT','CERTIFICATION:CREATE:ORGANIZATION')")
    public ApiResponse<CertificationDto> create(
            @Valid @RequestBody CreateCertificationRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(CertificationDto.from(certificationService.create(principal, request)), "Certification created");
    }

    @PutMapping("/{certificationId}")
    @PreAuthorize("hasAnyAuthority('CERTIFICATION:UPDATE:TEAM','CERTIFICATION:UPDATE:DEPARTMENT','CERTIFICATION:UPDATE:ORGANIZATION')")
    public ApiResponse<CertificationDto> update(
            @PathVariable UUID certificationId, @Valid @RequestBody UpdateCertificationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(CertificationDto.from(certificationService.update(principal, certificationId, request)), "Certification updated");
    }

    @DeleteMapping("/{certificationId}")
    @PreAuthorize("hasAnyAuthority('CERTIFICATION:DELETE:TEAM','CERTIFICATION:DELETE:DEPARTMENT','CERTIFICATION:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID certificationId, @AuthenticationPrincipal UserPrincipal principal) {
        certificationService.delete(principal, certificationId);
        return ApiResponse.ok(null, "Certification deleted");
    }
}
