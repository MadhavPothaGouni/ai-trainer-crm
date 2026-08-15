package com.aitrainercrm.platform.shift.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.shift.dto.CreateShiftTemplateRequest;
import com.aitrainercrm.platform.shift.dto.ShiftTemplateDto;
import com.aitrainercrm.platform.shift.dto.UpdateShiftTemplateRequest;
import com.aitrainercrm.platform.shift.entity.ShiftTemplate;
import com.aitrainercrm.platform.shift.service.ShiftTemplateService;
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

/** No OWN scope on SHIFT_TEMPLATE (see ShiftTemplateService's javadoc) - mirrors GroupClassController exactly. */
@RestController
@RequestMapping("/api/v1/shift-templates")
@RequiredArgsConstructor
public class ShiftTemplateController {

    private final ShiftTemplateService shiftTemplateService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SHIFT_TEMPLATE:READ:TEAM','SHIFT_TEMPLATE:READ:DEPARTMENT','SHIFT_TEMPLATE:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<ShiftTemplateDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<ShiftTemplate> page = shiftTemplateService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(ShiftTemplateDto::from).toList()));
    }

    @GetMapping("/{shiftTemplateId}")
    @PreAuthorize("hasAnyAuthority('SHIFT_TEMPLATE:READ:TEAM','SHIFT_TEMPLATE:READ:DEPARTMENT','SHIFT_TEMPLATE:READ:ORGANIZATION')")
    public ApiResponse<ShiftTemplateDto> get(@PathVariable UUID shiftTemplateId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ShiftTemplateDto.from(shiftTemplateService.get(principal, shiftTemplateId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('SHIFT_TEMPLATE:CREATE:TEAM','SHIFT_TEMPLATE:CREATE:DEPARTMENT','SHIFT_TEMPLATE:CREATE:ORGANIZATION')")
    public ApiResponse<ShiftTemplateDto> create(
            @Valid @RequestBody CreateShiftTemplateRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ShiftTemplateDto.from(shiftTemplateService.create(principal, request)), "Shift template created");
    }

    @PutMapping("/{shiftTemplateId}")
    @PreAuthorize("hasAnyAuthority('SHIFT_TEMPLATE:UPDATE:TEAM','SHIFT_TEMPLATE:UPDATE:DEPARTMENT','SHIFT_TEMPLATE:UPDATE:ORGANIZATION')")
    public ApiResponse<ShiftTemplateDto> update(
            @PathVariable UUID shiftTemplateId,
            @Valid @RequestBody UpdateShiftTemplateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ShiftTemplateDto.from(shiftTemplateService.update(principal, shiftTemplateId, request)), "Shift template updated");
    }

    @DeleteMapping("/{shiftTemplateId}")
    @PreAuthorize("hasAnyAuthority('SHIFT_TEMPLATE:DELETE:TEAM','SHIFT_TEMPLATE:DELETE:DEPARTMENT','SHIFT_TEMPLATE:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID shiftTemplateId, @AuthenticationPrincipal UserPrincipal principal) {
        shiftTemplateService.delete(principal, shiftTemplateId);
        return ApiResponse.ok(null, "Shift template deleted");
    }
}
