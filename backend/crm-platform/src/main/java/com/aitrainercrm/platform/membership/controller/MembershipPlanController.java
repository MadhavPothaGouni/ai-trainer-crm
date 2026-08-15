package com.aitrainercrm.platform.membership.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.membership.dto.CreateMembershipPlanRequest;
import com.aitrainercrm.platform.membership.dto.MembershipPlanDto;
import com.aitrainercrm.platform.membership.dto.UpdateMembershipPlanRequest;
import com.aitrainercrm.platform.membership.entity.MembershipPlan;
import com.aitrainercrm.platform.membership.service.MembershipPlanService;
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

/** No OWN scope on MEMBERSHIP_PLAN (see MembershipPlanService's javadoc) - mirrors ProductController exactly. */
@RestController
@RequestMapping("/api/v1/membership-plans")
@RequiredArgsConstructor
public class MembershipPlanController {

    private final MembershipPlanService membershipPlanService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('MEMBERSHIP_PLAN:READ:TEAM','MEMBERSHIP_PLAN:READ:DEPARTMENT','MEMBERSHIP_PLAN:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<MembershipPlanDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<MembershipPlan> page = membershipPlanService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(MembershipPlanDto::from).toList()));
    }

    @GetMapping("/{membershipPlanId}")
    @PreAuthorize("hasAnyAuthority('MEMBERSHIP_PLAN:READ:TEAM','MEMBERSHIP_PLAN:READ:DEPARTMENT','MEMBERSHIP_PLAN:READ:ORGANIZATION')")
    public ApiResponse<MembershipPlanDto> get(@PathVariable UUID membershipPlanId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(MembershipPlanDto.from(membershipPlanService.get(principal, membershipPlanId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('MEMBERSHIP_PLAN:CREATE:TEAM','MEMBERSHIP_PLAN:CREATE:DEPARTMENT','MEMBERSHIP_PLAN:CREATE:ORGANIZATION')")
    public ApiResponse<MembershipPlanDto> create(
            @Valid @RequestBody CreateMembershipPlanRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(MembershipPlanDto.from(membershipPlanService.create(principal, request)), "Membership plan created");
    }

    @PutMapping("/{membershipPlanId}")
    @PreAuthorize("hasAnyAuthority('MEMBERSHIP_PLAN:UPDATE:TEAM','MEMBERSHIP_PLAN:UPDATE:DEPARTMENT','MEMBERSHIP_PLAN:UPDATE:ORGANIZATION')")
    public ApiResponse<MembershipPlanDto> update(
            @PathVariable UUID membershipPlanId,
            @Valid @RequestBody UpdateMembershipPlanRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(MembershipPlanDto.from(membershipPlanService.update(principal, membershipPlanId, request)), "Membership plan updated");
    }

    @DeleteMapping("/{membershipPlanId}")
    @PreAuthorize("hasAnyAuthority('MEMBERSHIP_PLAN:DELETE:TEAM','MEMBERSHIP_PLAN:DELETE:DEPARTMENT','MEMBERSHIP_PLAN:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID membershipPlanId, @AuthenticationPrincipal UserPrincipal principal) {
        membershipPlanService.delete(principal, membershipPlanId);
        return ApiResponse.ok(null, "Membership plan deleted");
    }
}
