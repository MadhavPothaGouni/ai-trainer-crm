package com.aitrainercrm.platform.bodymeasurement.controller;

import com.aitrainercrm.platform.bodymeasurement.dto.BodyMeasurementDto;
import com.aitrainercrm.platform.bodymeasurement.dto.CreateBodyMeasurementRequest;
import com.aitrainercrm.platform.bodymeasurement.dto.UpdateBodyMeasurementRequest;
import com.aitrainercrm.platform.bodymeasurement.entity.BodyMeasurement;
import com.aitrainercrm.platform.bodymeasurement.service.BodyMeasurementService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Mirrors NutritionPlanController's shape, minus the /status sub-resource - this module has no status field. */
@RestController
@RequestMapping("/api/v1/body-measurements")
@RequiredArgsConstructor
public class BodyMeasurementController {

    private final BodyMeasurementService bodyMeasurementService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('BODY_MEASUREMENT:READ:OWN','BODY_MEASUREMENT:READ:TEAM','BODY_MEASUREMENT:READ:DEPARTMENT','BODY_MEASUREMENT:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<BodyMeasurementDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<BodyMeasurement> page = bodyMeasurementService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(BodyMeasurementDto::from).toList()));
    }

    @GetMapping("/{bodyMeasurementId}")
    @PreAuthorize("hasAnyAuthority('BODY_MEASUREMENT:READ:OWN','BODY_MEASUREMENT:READ:TEAM','BODY_MEASUREMENT:READ:DEPARTMENT','BODY_MEASUREMENT:READ:ORGANIZATION')")
    public ApiResponse<BodyMeasurementDto> get(@PathVariable UUID bodyMeasurementId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(BodyMeasurementDto.from(bodyMeasurementService.get(principal, bodyMeasurementId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('BODY_MEASUREMENT:CREATE:OWN','BODY_MEASUREMENT:CREATE:TEAM','BODY_MEASUREMENT:CREATE:DEPARTMENT','BODY_MEASUREMENT:CREATE:ORGANIZATION')")
    public ApiResponse<BodyMeasurementDto> create(
            @Valid @RequestBody CreateBodyMeasurementRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(BodyMeasurementDto.from(bodyMeasurementService.create(principal, request)), "Body measurement recorded");
    }

    @PutMapping("/{bodyMeasurementId}")
    @PreAuthorize("hasAnyAuthority('BODY_MEASUREMENT:UPDATE:OWN','BODY_MEASUREMENT:UPDATE:TEAM','BODY_MEASUREMENT:UPDATE:DEPARTMENT','BODY_MEASUREMENT:UPDATE:ORGANIZATION')")
    public ApiResponse<BodyMeasurementDto> update(
            @PathVariable UUID bodyMeasurementId, @Valid @RequestBody UpdateBodyMeasurementRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(BodyMeasurementDto.from(bodyMeasurementService.update(principal, bodyMeasurementId, request)), "Body measurement updated");
    }

    @DeleteMapping("/{bodyMeasurementId}")
    @PreAuthorize("hasAnyAuthority('BODY_MEASUREMENT:DELETE:OWN','BODY_MEASUREMENT:DELETE:TEAM','BODY_MEASUREMENT:DELETE:DEPARTMENT','BODY_MEASUREMENT:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID bodyMeasurementId, @AuthenticationPrincipal UserPrincipal principal) {
        bodyMeasurementService.delete(principal, bodyMeasurementId);
        return ApiResponse.ok(null, "Body measurement deleted");
    }
}
