package com.aitrainercrm.platform.membership.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.membership.dto.CreateMembershipRequest;
import com.aitrainercrm.platform.membership.dto.MembershipDto;
import com.aitrainercrm.platform.membership.dto.UpdateMembershipRequest;
import com.aitrainercrm.platform.membership.dto.UpdateMembershipStatusRequest;
import com.aitrainercrm.platform.membership.entity.Membership;
import com.aitrainercrm.platform.membership.service.MembershipService;
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

/** Mirrors ClientGoalController's shape exactly - see TicketController's own javadoc for the reasoning behind the coarse-@PreAuthorize-then-service-layer-record-check split. */
@RestController
@RequestMapping("/api/v1/memberships")
@RequiredArgsConstructor
public class MembershipController {

    private final MembershipService membershipService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('MEMBERSHIP:READ:OWN','MEMBERSHIP:READ:TEAM','MEMBERSHIP:READ:DEPARTMENT','MEMBERSHIP:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<MembershipDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<Membership> page = membershipService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(MembershipDto::from).toList()));
    }

    @GetMapping("/{membershipId}")
    @PreAuthorize("hasAnyAuthority('MEMBERSHIP:READ:OWN','MEMBERSHIP:READ:TEAM','MEMBERSHIP:READ:DEPARTMENT','MEMBERSHIP:READ:ORGANIZATION')")
    public ApiResponse<MembershipDto> get(@PathVariable UUID membershipId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(MembershipDto.from(membershipService.get(principal, membershipId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('MEMBERSHIP:CREATE:OWN','MEMBERSHIP:CREATE:TEAM','MEMBERSHIP:CREATE:DEPARTMENT','MEMBERSHIP:CREATE:ORGANIZATION')")
    public ApiResponse<MembershipDto> create(@Valid @RequestBody CreateMembershipRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(MembershipDto.from(membershipService.create(principal, request)), "Membership created");
    }

    @PutMapping("/{membershipId}")
    @PreAuthorize("hasAnyAuthority('MEMBERSHIP:UPDATE:OWN','MEMBERSHIP:UPDATE:TEAM','MEMBERSHIP:UPDATE:DEPARTMENT','MEMBERSHIP:UPDATE:ORGANIZATION')")
    public ApiResponse<MembershipDto> update(
            @PathVariable UUID membershipId, @Valid @RequestBody UpdateMembershipRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(MembershipDto.from(membershipService.update(principal, membershipId, request)), "Membership updated");
    }

    @PatchMapping("/{membershipId}/status")
    @PreAuthorize("hasAnyAuthority('MEMBERSHIP:UPDATE:OWN','MEMBERSHIP:UPDATE:TEAM','MEMBERSHIP:UPDATE:DEPARTMENT','MEMBERSHIP:UPDATE:ORGANIZATION')")
    public ApiResponse<MembershipDto> updateStatus(
            @PathVariable UUID membershipId, @Valid @RequestBody UpdateMembershipStatusRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(MembershipDto.from(membershipService.updateStatus(principal, membershipId, request.status())), "Status updated");
    }

    @DeleteMapping("/{membershipId}")
    @PreAuthorize("hasAnyAuthority('MEMBERSHIP:DELETE:OWN','MEMBERSHIP:DELETE:TEAM','MEMBERSHIP:DELETE:DEPARTMENT','MEMBERSHIP:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID membershipId, @AuthenticationPrincipal UserPrincipal principal) {
        membershipService.delete(principal, membershipId);
        return ApiResponse.ok(null, "Membership deleted");
    }
}
