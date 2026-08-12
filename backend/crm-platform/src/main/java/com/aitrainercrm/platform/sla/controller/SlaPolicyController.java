package com.aitrainercrm.platform.sla.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.sla.dto.CreateSlaPolicyRequest;
import com.aitrainercrm.platform.sla.dto.SlaPolicyDto;
import com.aitrainercrm.platform.sla.dto.UpdateSlaPolicyRequest;
import com.aitrainercrm.platform.sla.entity.SlaPolicy;
import com.aitrainercrm.platform.sla.service.SlaPolicyService;
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

/** Admin config CRUD, entirely gated by a single ORGANIZATION-scope authority per action - same shape as CustomFieldController, no OWN/TEAM/DEPARTMENT variant exists for SLA_POLICY. */
@RestController
@RequestMapping("/api/v1/sla-policies")
@RequiredArgsConstructor
public class SlaPolicyController {

    private final SlaPolicyService slaPolicyService;

    @GetMapping
    @PreAuthorize("hasAuthority('SLA_POLICY:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<SlaPolicyDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<SlaPolicy> page = slaPolicyService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(SlaPolicyDto::from).toList()));
    }

    @GetMapping("/{policyId}")
    @PreAuthorize("hasAuthority('SLA_POLICY:READ:ORGANIZATION')")
    public ApiResponse<SlaPolicyDto> get(@PathVariable UUID policyId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(SlaPolicyDto.from(slaPolicyService.get(principal, policyId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SLA_POLICY:CREATE:ORGANIZATION')")
    public ApiResponse<SlaPolicyDto> create(@Valid @RequestBody CreateSlaPolicyRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(SlaPolicyDto.from(slaPolicyService.create(principal, request)), "SLA policy created");
    }

    @PutMapping("/{policyId}")
    @PreAuthorize("hasAuthority('SLA_POLICY:UPDATE:ORGANIZATION')")
    public ApiResponse<SlaPolicyDto> update(
            @PathVariable UUID policyId, @Valid @RequestBody UpdateSlaPolicyRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(SlaPolicyDto.from(slaPolicyService.update(principal, policyId, request)), "SLA policy updated");
    }

    @DeleteMapping("/{policyId}")
    @PreAuthorize("hasAuthority('SLA_POLICY:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID policyId, @AuthenticationPrincipal UserPrincipal principal) {
        slaPolicyService.delete(principal, policyId);
        return ApiResponse.ok(null, "SLA policy deleted");
    }
}
