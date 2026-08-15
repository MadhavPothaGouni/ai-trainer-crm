package com.aitrainercrm.platform.intakeform.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.intakeform.dto.CreateIntakeFormSubmissionRequest;
import com.aitrainercrm.platform.intakeform.dto.IntakeFormSubmissionDto;
import com.aitrainercrm.platform.intakeform.dto.UpdateIntakeFormSubmissionRequest;
import com.aitrainercrm.platform.intakeform.entity.IntakeFormSubmission;
import com.aitrainercrm.platform.intakeform.service.IntakeFormSubmissionService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Mirrors ProgressPhotoController's CRUD shape - no PATCH .../status endpoint, see IntakeFormSubmission's javadoc. */
@RestController
@RequestMapping("/api/v1/intake-form-submissions")
@RequiredArgsConstructor
public class IntakeFormSubmissionController {

    private final IntakeFormSubmissionService intakeFormSubmissionService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('INTAKE_FORM_SUBMISSION:READ:OWN','INTAKE_FORM_SUBMISSION:READ:TEAM','INTAKE_FORM_SUBMISSION:READ:DEPARTMENT','INTAKE_FORM_SUBMISSION:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<IntakeFormSubmissionDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<IntakeFormSubmission> page = intakeFormSubmissionService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(IntakeFormSubmissionDto::from).toList()));
    }

    @GetMapping("/{intakeFormSubmissionId}")
    @PreAuthorize("hasAnyAuthority('INTAKE_FORM_SUBMISSION:READ:OWN','INTAKE_FORM_SUBMISSION:READ:TEAM','INTAKE_FORM_SUBMISSION:READ:DEPARTMENT','INTAKE_FORM_SUBMISSION:READ:ORGANIZATION')")
    public ApiResponse<IntakeFormSubmissionDto> get(@PathVariable UUID intakeFormSubmissionId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(IntakeFormSubmissionDto.from(intakeFormSubmissionService.get(principal, intakeFormSubmissionId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('INTAKE_FORM_SUBMISSION:CREATE:OWN','INTAKE_FORM_SUBMISSION:CREATE:TEAM','INTAKE_FORM_SUBMISSION:CREATE:DEPARTMENT','INTAKE_FORM_SUBMISSION:CREATE:ORGANIZATION')")
    public ApiResponse<IntakeFormSubmissionDto> create(
            @Valid @RequestBody CreateIntakeFormSubmissionRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(IntakeFormSubmissionDto.from(intakeFormSubmissionService.create(principal, request)), "Intake form submission recorded");
    }

    @PutMapping("/{intakeFormSubmissionId}")
    @PreAuthorize("hasAnyAuthority('INTAKE_FORM_SUBMISSION:UPDATE:OWN','INTAKE_FORM_SUBMISSION:UPDATE:TEAM','INTAKE_FORM_SUBMISSION:UPDATE:DEPARTMENT','INTAKE_FORM_SUBMISSION:UPDATE:ORGANIZATION')")
    public ApiResponse<IntakeFormSubmissionDto> update(
            @PathVariable UUID intakeFormSubmissionId,
            @Valid @RequestBody UpdateIntakeFormSubmissionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(
                IntakeFormSubmissionDto.from(intakeFormSubmissionService.update(principal, intakeFormSubmissionId, request)),
                "Intake form submission updated");
    }

    @DeleteMapping("/{intakeFormSubmissionId}")
    @PreAuthorize("hasAnyAuthority('INTAKE_FORM_SUBMISSION:DELETE:OWN','INTAKE_FORM_SUBMISSION:DELETE:TEAM','INTAKE_FORM_SUBMISSION:DELETE:DEPARTMENT','INTAKE_FORM_SUBMISSION:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID intakeFormSubmissionId, @AuthenticationPrincipal UserPrincipal principal) {
        intakeFormSubmissionService.delete(principal, intakeFormSubmissionId);
        return ApiResponse.ok(null, "Intake form submission deleted");
    }
}
