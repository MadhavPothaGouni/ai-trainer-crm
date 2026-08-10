package com.aitrainercrm.platform.opportunity.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.AssignOwnerRequest;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.opportunity.dto.CreateOpportunityRequest;
import com.aitrainercrm.platform.opportunity.dto.OpportunityDto;
import com.aitrainercrm.platform.opportunity.dto.UpdateOpportunityRequest;
import com.aitrainercrm.platform.opportunity.dto.UpdateOpportunityStageRequest;
import com.aitrainercrm.platform.opportunity.entity.Opportunity;
import com.aitrainercrm.platform.opportunity.service.OpportunityService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/opportunities")
@RequiredArgsConstructor
public class OpportunityController {

    private final OpportunityService opportunityService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('OPPORTUNITY:READ:OWN','OPPORTUNITY:READ:TEAM','OPPORTUNITY:READ:DEPARTMENT','OPPORTUNITY:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<OpportunityDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<Opportunity> page = opportunityService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(OpportunityDto::from).toList()));
    }

    @GetMapping("/{opportunityId}")
    @PreAuthorize("hasAnyAuthority('OPPORTUNITY:READ:OWN','OPPORTUNITY:READ:TEAM','OPPORTUNITY:READ:DEPARTMENT','OPPORTUNITY:READ:ORGANIZATION')")
    public ApiResponse<OpportunityDto> get(@PathVariable UUID opportunityId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(OpportunityDto.from(opportunityService.get(principal, opportunityId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('OPPORTUNITY:CREATE:OWN','OPPORTUNITY:CREATE:TEAM','OPPORTUNITY:CREATE:DEPARTMENT','OPPORTUNITY:CREATE:ORGANIZATION')")
    public ApiResponse<OpportunityDto> create(@Valid @RequestBody CreateOpportunityRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(OpportunityDto.from(opportunityService.create(principal, request)), "Opportunity created");
    }

    @PutMapping("/{opportunityId}")
    @PreAuthorize("hasAnyAuthority('OPPORTUNITY:UPDATE:OWN','OPPORTUNITY:UPDATE:TEAM','OPPORTUNITY:UPDATE:DEPARTMENT','OPPORTUNITY:UPDATE:ORGANIZATION')")
    public ApiResponse<OpportunityDto> update(
            @PathVariable UUID opportunityId, @Valid @RequestBody UpdateOpportunityRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(OpportunityDto.from(opportunityService.update(principal, opportunityId, request)), "Opportunity updated");
    }

    @PatchMapping("/{opportunityId}/stage")
    @PreAuthorize("hasAnyAuthority('OPPORTUNITY:UPDATE:OWN','OPPORTUNITY:UPDATE:TEAM','OPPORTUNITY:UPDATE:DEPARTMENT','OPPORTUNITY:UPDATE:ORGANIZATION')")
    public ApiResponse<OpportunityDto> updateStage(
            @PathVariable UUID opportunityId, @Valid @RequestBody UpdateOpportunityStageRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(OpportunityDto.from(opportunityService.updateStage(principal, opportunityId, request.stage())), "Stage updated");
    }

    @DeleteMapping("/{opportunityId}")
    @PreAuthorize("hasAnyAuthority('OPPORTUNITY:DELETE:OWN','OPPORTUNITY:DELETE:TEAM','OPPORTUNITY:DELETE:DEPARTMENT','OPPORTUNITY:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID opportunityId, @AuthenticationPrincipal UserPrincipal principal) {
        opportunityService.delete(principal, opportunityId);
        return ApiResponse.ok(null, "Opportunity deleted");
    }

    @PatchMapping("/{opportunityId}/owner")
    @PreAuthorize("hasAnyAuthority('OPPORTUNITY:ASSIGN:OWN','OPPORTUNITY:ASSIGN:TEAM','OPPORTUNITY:ASSIGN:DEPARTMENT','OPPORTUNITY:ASSIGN:ORGANIZATION')")
    public ApiResponse<OpportunityDto> assignOwner(
            @PathVariable UUID opportunityId, @Valid @RequestBody AssignOwnerRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(OpportunityDto.from(opportunityService.assignOwner(principal, opportunityId, request.ownerId())), "Owner updated");
    }
}
