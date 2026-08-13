package com.aitrainercrm.platform.commission.controller;

import com.aitrainercrm.platform.commission.dto.CommissionPlanDto;
import com.aitrainercrm.platform.commission.dto.CreateCommissionPlanRequest;
import com.aitrainercrm.platform.commission.dto.UpdateCommissionPlanRequest;
import com.aitrainercrm.platform.commission.entity.CommissionPlan;
import com.aitrainercrm.platform.commission.service.CommissionPlanService;
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

/** Only ORGANIZATION scope is seeded for COMMISSION_PLAN (V29) - single hasAuthority(...), same
 * style TeamController/RegionController use for their own ORGANIZATION-only resources. */
@RestController
@RequestMapping("/api/v1/commission-plans")
@RequiredArgsConstructor
public class CommissionPlanController {

    private final CommissionPlanService commissionPlanService;

    @GetMapping
    @PreAuthorize("hasAuthority('COMMISSION_PLAN:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<CommissionPlanDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<CommissionPlan> page = commissionPlanService.list(principal.getOrganizationId(), pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(CommissionPlanDto::from).toList()));
    }

    @GetMapping("/{planId}")
    @PreAuthorize("hasAuthority('COMMISSION_PLAN:READ:ORGANIZATION')")
    public ApiResponse<CommissionPlanDto> get(@PathVariable UUID planId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(CommissionPlanDto.from(commissionPlanService.get(principal.getOrganizationId(), planId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('COMMISSION_PLAN:CREATE:ORGANIZATION')")
    public ApiResponse<CommissionPlanDto> create(
            @Valid @RequestBody CreateCommissionPlanRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(
                CommissionPlanDto.from(commissionPlanService.create(principal.getOrganizationId(), request)), "Commission plan created");
    }

    @PutMapping("/{planId}")
    @PreAuthorize("hasAuthority('COMMISSION_PLAN:UPDATE:ORGANIZATION')")
    public ApiResponse<CommissionPlanDto> update(
            @PathVariable UUID planId, @Valid @RequestBody UpdateCommissionPlanRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(
                CommissionPlanDto.from(commissionPlanService.update(principal.getOrganizationId(), planId, request)),
                "Commission plan updated");
    }

    @DeleteMapping("/{planId}")
    @PreAuthorize("hasAuthority('COMMISSION_PLAN:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID planId, @AuthenticationPrincipal UserPrincipal principal) {
        commissionPlanService.delete(principal.getOrganizationId(), planId);
        return ApiResponse.ok(null, "Commission plan deleted");
    }
}
