package com.aitrainercrm.platform.organization.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.organization.dto.OrganizationDto;
import com.aitrainercrm.platform.organization.dto.UpdateOrganizationRequest;
import com.aitrainercrm.platform.organization.entity.Organization;
import com.aitrainercrm.platform.organization.service.OrganizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * There's deliberately no "list organizations" or "get organization by
 * id" endpoint - a user only ever has one organization (their own, off
 * the JWT), and every other tenant's organization record must stay
 * invisible to them. "me" is the only organization anyone can ever look up.
 */
@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    @GetMapping("/me")
    public ApiResponse<OrganizationDto> me(@AuthenticationPrincipal UserPrincipal principal) {
        Organization organization = organizationService.getById(principal.getOrganizationId());
        return ApiResponse.ok(OrganizationDto.from(organization));
    }

    @PatchMapping("/me")
    @PreAuthorize("hasAuthority('ORGANIZATION:UPDATE:ORGANIZATION')")
    public ApiResponse<OrganizationDto> updateMe(
            @Valid @RequestBody UpdateOrganizationRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        Organization organization = organizationService.update(principal.getOrganizationId(), request);
        return ApiResponse.ok(OrganizationDto.from(organization), "Organization updated");
    }
}
