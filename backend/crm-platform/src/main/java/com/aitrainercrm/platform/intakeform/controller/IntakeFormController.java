package com.aitrainercrm.platform.intakeform.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.intakeform.dto.CreateIntakeFormRequest;
import com.aitrainercrm.platform.intakeform.dto.IntakeFormDto;
import com.aitrainercrm.platform.intakeform.dto.UpdateIntakeFormRequest;
import com.aitrainercrm.platform.intakeform.entity.IntakeForm;
import com.aitrainercrm.platform.intakeform.service.IntakeFormService;
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

/** No OWN scope on INTAKE_FORM (see IntakeFormService's javadoc) - mirrors RoomController exactly. */
@RestController
@RequestMapping("/api/v1/intake-forms")
@RequiredArgsConstructor
public class IntakeFormController {

    private final IntakeFormService intakeFormService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('INTAKE_FORM:READ:TEAM','INTAKE_FORM:READ:DEPARTMENT','INTAKE_FORM:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<IntakeFormDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<IntakeForm> page = intakeFormService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(IntakeFormDto::from).toList()));
    }

    @GetMapping("/{intakeFormId}")
    @PreAuthorize("hasAnyAuthority('INTAKE_FORM:READ:TEAM','INTAKE_FORM:READ:DEPARTMENT','INTAKE_FORM:READ:ORGANIZATION')")
    public ApiResponse<IntakeFormDto> get(@PathVariable UUID intakeFormId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(IntakeFormDto.from(intakeFormService.get(principal, intakeFormId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('INTAKE_FORM:CREATE:TEAM','INTAKE_FORM:CREATE:DEPARTMENT','INTAKE_FORM:CREATE:ORGANIZATION')")
    public ApiResponse<IntakeFormDto> create(@Valid @RequestBody CreateIntakeFormRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(IntakeFormDto.from(intakeFormService.create(principal, request)), "Intake form added");
    }

    @PutMapping("/{intakeFormId}")
    @PreAuthorize("hasAnyAuthority('INTAKE_FORM:UPDATE:TEAM','INTAKE_FORM:UPDATE:DEPARTMENT','INTAKE_FORM:UPDATE:ORGANIZATION')")
    public ApiResponse<IntakeFormDto> update(
            @PathVariable UUID intakeFormId, @Valid @RequestBody UpdateIntakeFormRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(IntakeFormDto.from(intakeFormService.update(principal, intakeFormId, request)), "Intake form updated");
    }

    @DeleteMapping("/{intakeFormId}")
    @PreAuthorize("hasAnyAuthority('INTAKE_FORM:DELETE:TEAM','INTAKE_FORM:DELETE:DEPARTMENT','INTAKE_FORM:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID intakeFormId, @AuthenticationPrincipal UserPrincipal principal) {
        intakeFormService.delete(principal, intakeFormId);
        return ApiResponse.ok(null, "Intake form deleted");
    }
}
