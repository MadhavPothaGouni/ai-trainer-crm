package com.aitrainercrm.platform.region.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.region.dto.CreateRegionRequest;
import com.aitrainercrm.platform.region.dto.RegionDto;
import com.aitrainercrm.platform.region.dto.RegionRollupDto;
import com.aitrainercrm.platform.region.dto.UpdateRegionRequest;
import com.aitrainercrm.platform.region.service.RegionService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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

/** Only ORGANIZATION scope is seeded for REGION (V28) - single hasAuthority(...), same style
 * TeamController/OrganizationController use for their own ORGANIZATION-only resources. {@code
 * list} returns the whole tree unpaginated (same shape TeamRepository's own
 * findByOrganizationIdAndDeletedAtIsNullOrderByNameAsc gives the frontend) rather than a Page -
 * an org's region count is expected to be small enough that client-side tree-building from one flat
 * list is simpler than paginating a hierarchy, which doesn't page cleanly anyway. */
@RestController
@RequestMapping("/api/v1/regions")
@RequiredArgsConstructor
public class RegionController {

    private final RegionService regionService;

    @GetMapping
    @PreAuthorize("hasAuthority('REGION:READ:ORGANIZATION')")
    public ApiResponse<List<RegionDto>> list(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(regionService.list(principal.getOrganizationId()).stream().map(RegionDto::from).toList());
    }

    @GetMapping("/{regionId}")
    @PreAuthorize("hasAuthority('REGION:READ:ORGANIZATION')")
    public ApiResponse<RegionDto> get(@PathVariable UUID regionId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(RegionDto.from(regionService.get(principal.getOrganizationId(), regionId)));
    }

    @GetMapping("/{regionId}/rollup")
    @PreAuthorize("hasAuthority('REGION:READ:ORGANIZATION')")
    public ApiResponse<RegionRollupDto> rollup(@PathVariable UUID regionId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(regionService.rollup(principal.getOrganizationId(), regionId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('REGION:CREATE:ORGANIZATION')")
    public ApiResponse<RegionDto> create(@Valid @RequestBody CreateRegionRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(RegionDto.from(regionService.create(principal.getOrganizationId(), request)), "Region created");
    }

    @PutMapping("/{regionId}")
    @PreAuthorize("hasAuthority('REGION:UPDATE:ORGANIZATION')")
    public ApiResponse<RegionDto> update(
            @PathVariable UUID regionId, @Valid @RequestBody UpdateRegionRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(RegionDto.from(regionService.update(principal.getOrganizationId(), regionId, request)), "Region updated");
    }

    @DeleteMapping("/{regionId}")
    @PreAuthorize("hasAuthority('REGION:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID regionId, @AuthenticationPrincipal UserPrincipal principal) {
        regionService.delete(principal.getOrganizationId(), regionId);
        return ApiResponse.ok(null, "Region deleted");
    }
}
