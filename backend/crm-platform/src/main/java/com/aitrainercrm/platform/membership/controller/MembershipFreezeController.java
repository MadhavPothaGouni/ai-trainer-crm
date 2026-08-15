package com.aitrainercrm.platform.membership.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.membership.dto.CreateMembershipFreezeRequest;
import com.aitrainercrm.platform.membership.dto.MembershipFreezeDto;
import com.aitrainercrm.platform.membership.dto.UpdateMembershipFreezeRequest;
import com.aitrainercrm.platform.membership.dto.UpdateMembershipFreezeStatusRequest;
import com.aitrainercrm.platform.membership.entity.MembershipFreeze;
import com.aitrainercrm.platform.membership.service.MembershipFreezeService;
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

/** Standard CRUD plus a PATCH .../status endpoint - mirrors RoomBookingController's shape. */
@RestController
@RequestMapping("/api/v1/membership-freezes")
@RequiredArgsConstructor
public class MembershipFreezeController {

    private final MembershipFreezeService membershipFreezeService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('MEMBERSHIP_FREEZE:READ:OWN','MEMBERSHIP_FREEZE:READ:TEAM','MEMBERSHIP_FREEZE:READ:DEPARTMENT','MEMBERSHIP_FREEZE:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<MembershipFreezeDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<MembershipFreeze> page = membershipFreezeService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(MembershipFreezeDto::from).toList()));
    }

    @GetMapping("/{membershipFreezeId}")
    @PreAuthorize("hasAnyAuthority('MEMBERSHIP_FREEZE:READ:OWN','MEMBERSHIP_FREEZE:READ:TEAM','MEMBERSHIP_FREEZE:READ:DEPARTMENT','MEMBERSHIP_FREEZE:READ:ORGANIZATION')")
    public ApiResponse<MembershipFreezeDto> get(@PathVariable UUID membershipFreezeId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(MembershipFreezeDto.from(membershipFreezeService.get(principal, membershipFreezeId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('MEMBERSHIP_FREEZE:CREATE:OWN','MEMBERSHIP_FREEZE:CREATE:TEAM','MEMBERSHIP_FREEZE:CREATE:DEPARTMENT','MEMBERSHIP_FREEZE:CREATE:ORGANIZATION')")
    public ApiResponse<MembershipFreezeDto> create(
            @Valid @RequestBody CreateMembershipFreezeRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(MembershipFreezeDto.from(membershipFreezeService.create(principal, request)), "Freeze requested");
    }

    @PutMapping("/{membershipFreezeId}")
    @PreAuthorize("hasAnyAuthority('MEMBERSHIP_FREEZE:UPDATE:OWN','MEMBERSHIP_FREEZE:UPDATE:TEAM','MEMBERSHIP_FREEZE:UPDATE:DEPARTMENT','MEMBERSHIP_FREEZE:UPDATE:ORGANIZATION')")
    public ApiResponse<MembershipFreezeDto> update(
            @PathVariable UUID membershipFreezeId,
            @Valid @RequestBody UpdateMembershipFreezeRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(MembershipFreezeDto.from(membershipFreezeService.update(principal, membershipFreezeId, request)), "Freeze updated");
    }

    @PatchMapping("/{membershipFreezeId}/status")
    @PreAuthorize("hasAnyAuthority('MEMBERSHIP_FREEZE:UPDATE:OWN','MEMBERSHIP_FREEZE:UPDATE:TEAM','MEMBERSHIP_FREEZE:UPDATE:DEPARTMENT','MEMBERSHIP_FREEZE:UPDATE:ORGANIZATION')")
    public ApiResponse<MembershipFreezeDto> updateStatus(
            @PathVariable UUID membershipFreezeId,
            @Valid @RequestBody UpdateMembershipFreezeStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(
                MembershipFreezeDto.from(membershipFreezeService.updateStatus(principal, membershipFreezeId, request.status())),
                "Freeze status updated");
    }

    @DeleteMapping("/{membershipFreezeId}")
    @PreAuthorize("hasAnyAuthority('MEMBERSHIP_FREEZE:DELETE:OWN','MEMBERSHIP_FREEZE:DELETE:TEAM','MEMBERSHIP_FREEZE:DELETE:DEPARTMENT','MEMBERSHIP_FREEZE:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID membershipFreezeId, @AuthenticationPrincipal UserPrincipal principal) {
        membershipFreezeService.delete(principal, membershipFreezeId);
        return ApiResponse.ok(null, "Freeze deleted");
    }
}
