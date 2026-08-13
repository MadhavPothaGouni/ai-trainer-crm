package com.aitrainercrm.platform.sequence.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.sequence.dto.CreateSequenceEnrollmentRequest;
import com.aitrainercrm.platform.sequence.dto.SequenceEnrollmentDto;
import com.aitrainercrm.platform.sequence.dto.UpdateSequenceEnrollmentStatusRequest;
import com.aitrainercrm.platform.sequence.entity.SequenceEnrollment;
import com.aitrainercrm.platform.sequence.service.SequenceEnrollmentService;
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

/** Mirrors CourseEnrollmentController's shape exactly - see SequenceEnrollmentService's javadoc for the OWN/TEAM/DEPARTMENT/ORGANIZATION reasoning. */
@RestController
@RequestMapping("/api/v1/sequence-enrollments")
@RequiredArgsConstructor
public class SequenceEnrollmentController {

    private final SequenceEnrollmentService sequenceEnrollmentService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SEQUENCE_ENROLLMENT:READ:OWN','SEQUENCE_ENROLLMENT:READ:TEAM','SEQUENCE_ENROLLMENT:READ:DEPARTMENT','SEQUENCE_ENROLLMENT:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<SequenceEnrollmentDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<SequenceEnrollment> page = sequenceEnrollmentService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(SequenceEnrollmentDto::from).toList()));
    }

    @GetMapping("/{enrollmentId}")
    @PreAuthorize("hasAnyAuthority('SEQUENCE_ENROLLMENT:READ:OWN','SEQUENCE_ENROLLMENT:READ:TEAM','SEQUENCE_ENROLLMENT:READ:DEPARTMENT','SEQUENCE_ENROLLMENT:READ:ORGANIZATION')")
    public ApiResponse<SequenceEnrollmentDto> get(@PathVariable UUID enrollmentId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(SequenceEnrollmentDto.from(sequenceEnrollmentService.get(principal, enrollmentId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('SEQUENCE_ENROLLMENT:CREATE:OWN','SEQUENCE_ENROLLMENT:CREATE:TEAM','SEQUENCE_ENROLLMENT:CREATE:DEPARTMENT','SEQUENCE_ENROLLMENT:CREATE:ORGANIZATION')")
    public ApiResponse<SequenceEnrollmentDto> create(
            @Valid @RequestBody CreateSequenceEnrollmentRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(SequenceEnrollmentDto.from(sequenceEnrollmentService.create(principal, request)), "Enrolled");
    }

    /** See SequenceEnrollmentService#advance's javadoc - moves to the next step, auto-completing once the step list is exhausted. */
    @PatchMapping("/{enrollmentId}/advance")
    @PreAuthorize("hasAnyAuthority('SEQUENCE_ENROLLMENT:UPDATE:OWN','SEQUENCE_ENROLLMENT:UPDATE:TEAM','SEQUENCE_ENROLLMENT:UPDATE:DEPARTMENT','SEQUENCE_ENROLLMENT:UPDATE:ORGANIZATION')")
    public ApiResponse<SequenceEnrollmentDto> advance(@PathVariable UUID enrollmentId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(SequenceEnrollmentDto.from(sequenceEnrollmentService.advance(principal, enrollmentId)), "Advanced to next step");
    }

    @PatchMapping("/{enrollmentId}/status")
    @PreAuthorize("hasAnyAuthority('SEQUENCE_ENROLLMENT:UPDATE:OWN','SEQUENCE_ENROLLMENT:UPDATE:TEAM','SEQUENCE_ENROLLMENT:UPDATE:DEPARTMENT','SEQUENCE_ENROLLMENT:UPDATE:ORGANIZATION')")
    public ApiResponse<SequenceEnrollmentDto> updateStatus(
            @PathVariable UUID enrollmentId, @Valid @RequestBody UpdateSequenceEnrollmentStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(
                SequenceEnrollmentDto.from(sequenceEnrollmentService.updateStatus(principal, enrollmentId, request.status())), "Status updated");
    }

    @DeleteMapping("/{enrollmentId}")
    @PreAuthorize("hasAnyAuthority('SEQUENCE_ENROLLMENT:DELETE:OWN','SEQUENCE_ENROLLMENT:DELETE:TEAM','SEQUENCE_ENROLLMENT:DELETE:DEPARTMENT','SEQUENCE_ENROLLMENT:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID enrollmentId, @AuthenticationPrincipal UserPrincipal principal) {
        sequenceEnrollmentService.delete(principal, enrollmentId);
        return ApiResponse.ok(null, "Enrollment removed");
    }
}
