package com.aitrainercrm.platform.lead.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.AssignOwnerRequest;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.lead.dto.ConvertLeadRequest;
import com.aitrainercrm.platform.lead.dto.CreateLeadRequest;
import com.aitrainercrm.platform.lead.dto.LeadConversionResult;
import com.aitrainercrm.platform.lead.dto.LeadDto;
import com.aitrainercrm.platform.lead.dto.UpdateLeadRequest;
import com.aitrainercrm.platform.lead.dto.UpdateLeadStatusRequest;
import com.aitrainercrm.platform.lead.entity.Lead;
import com.aitrainercrm.platform.lead.service.LeadService;
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
@RequestMapping("/api/v1/leads")
@RequiredArgsConstructor
public class LeadController {

    private final LeadService leadService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('LEAD:READ:OWN','LEAD:READ:TEAM','LEAD:READ:DEPARTMENT','LEAD:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<LeadDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<Lead> page = leadService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(LeadDto::from).toList()));
    }

    @GetMapping("/{leadId}")
    @PreAuthorize("hasAnyAuthority('LEAD:READ:OWN','LEAD:READ:TEAM','LEAD:READ:DEPARTMENT','LEAD:READ:ORGANIZATION')")
    public ApiResponse<LeadDto> get(@PathVariable UUID leadId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(LeadDto.from(leadService.get(principal, leadId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('LEAD:CREATE:OWN','LEAD:CREATE:TEAM','LEAD:CREATE:DEPARTMENT','LEAD:CREATE:ORGANIZATION')")
    public ApiResponse<LeadDto> create(@Valid @RequestBody CreateLeadRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(LeadDto.from(leadService.create(principal, request)), "Lead created");
    }

    @PutMapping("/{leadId}")
    @PreAuthorize("hasAnyAuthority('LEAD:UPDATE:OWN','LEAD:UPDATE:TEAM','LEAD:UPDATE:DEPARTMENT','LEAD:UPDATE:ORGANIZATION')")
    public ApiResponse<LeadDto> update(
            @PathVariable UUID leadId, @Valid @RequestBody UpdateLeadRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(LeadDto.from(leadService.update(principal, leadId, request)), "Lead updated");
    }

    @PatchMapping("/{leadId}/status")
    @PreAuthorize("hasAnyAuthority('LEAD:UPDATE:OWN','LEAD:UPDATE:TEAM','LEAD:UPDATE:DEPARTMENT','LEAD:UPDATE:ORGANIZATION')")
    public ApiResponse<LeadDto> updateStatus(
            @PathVariable UUID leadId, @Valid @RequestBody UpdateLeadStatusRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(LeadDto.from(leadService.updateStatus(principal, leadId, request.status())), "Status updated");
    }

    @DeleteMapping("/{leadId}")
    @PreAuthorize("hasAnyAuthority('LEAD:DELETE:OWN','LEAD:DELETE:TEAM','LEAD:DELETE:DEPARTMENT','LEAD:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID leadId, @AuthenticationPrincipal UserPrincipal principal) {
        leadService.delete(principal, leadId);
        return ApiResponse.ok(null, "Lead deleted");
    }

    @PatchMapping("/{leadId}/owner")
    @PreAuthorize("hasAnyAuthority('LEAD:ASSIGN:OWN','LEAD:ASSIGN:TEAM','LEAD:ASSIGN:DEPARTMENT','LEAD:ASSIGN:ORGANIZATION')")
    public ApiResponse<LeadDto> assignOwner(
            @PathVariable UUID leadId, @Valid @RequestBody AssignOwnerRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(LeadDto.from(leadService.assignOwner(principal, leadId, request.ownerId())), "Owner updated");
    }

    /**
     * Requires LEAD:UPDATE (converting mutates the lead) - the sub-permissions
     * needed for whichever of Account/Contact/Opportunity actually get
     * created are checked inside LeadService#convert once it knows what's
     * being created, since that depends on the request body (existingAccountId,
     * createOpportunity).
     */
    @PostMapping("/{leadId}/convert")
    @PreAuthorize("hasAnyAuthority('LEAD:UPDATE:OWN','LEAD:UPDATE:TEAM','LEAD:UPDATE:DEPARTMENT','LEAD:UPDATE:ORGANIZATION')")
    public ApiResponse<LeadConversionResult> convert(
            @PathVariable UUID leadId, @Valid @RequestBody ConvertLeadRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(leadService.convert(principal, leadId, request), "Lead converted");
    }
}
