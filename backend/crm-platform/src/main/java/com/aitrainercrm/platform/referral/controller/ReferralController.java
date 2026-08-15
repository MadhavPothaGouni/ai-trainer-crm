package com.aitrainercrm.platform.referral.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.referral.dto.CreateReferralRequest;
import com.aitrainercrm.platform.referral.dto.ReferralDto;
import com.aitrainercrm.platform.referral.dto.UpdateReferralRequest;
import com.aitrainercrm.platform.referral.dto.UpdateReferralStatusRequest;
import com.aitrainercrm.platform.referral.entity.Referral;
import com.aitrainercrm.platform.referral.service.ReferralService;
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

/** Mirrors ClientGoalController's shape, plus a dedicated PATCH .../reward endpoint - see ReferralService#issueReward's javadoc for why that's separate from the status transition. */
@RestController
@RequestMapping("/api/v1/referrals")
@RequiredArgsConstructor
public class ReferralController {

    private final ReferralService referralService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('REFERRAL:READ:OWN','REFERRAL:READ:TEAM','REFERRAL:READ:DEPARTMENT','REFERRAL:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<ReferralDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<Referral> page = referralService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(ReferralDto::from).toList()));
    }

    @GetMapping("/{referralId}")
    @PreAuthorize("hasAnyAuthority('REFERRAL:READ:OWN','REFERRAL:READ:TEAM','REFERRAL:READ:DEPARTMENT','REFERRAL:READ:ORGANIZATION')")
    public ApiResponse<ReferralDto> get(@PathVariable UUID referralId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ReferralDto.from(referralService.get(principal, referralId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('REFERRAL:CREATE:OWN','REFERRAL:CREATE:TEAM','REFERRAL:CREATE:DEPARTMENT','REFERRAL:CREATE:ORGANIZATION')")
    public ApiResponse<ReferralDto> create(@Valid @RequestBody CreateReferralRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ReferralDto.from(referralService.create(principal, request)), "Referral created");
    }

    @PutMapping("/{referralId}")
    @PreAuthorize("hasAnyAuthority('REFERRAL:UPDATE:OWN','REFERRAL:UPDATE:TEAM','REFERRAL:UPDATE:DEPARTMENT','REFERRAL:UPDATE:ORGANIZATION')")
    public ApiResponse<ReferralDto> update(
            @PathVariable UUID referralId, @Valid @RequestBody UpdateReferralRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ReferralDto.from(referralService.update(principal, referralId, request)), "Referral updated");
    }

    @PatchMapping("/{referralId}/status")
    @PreAuthorize("hasAnyAuthority('REFERRAL:UPDATE:OWN','REFERRAL:UPDATE:TEAM','REFERRAL:UPDATE:DEPARTMENT','REFERRAL:UPDATE:ORGANIZATION')")
    public ApiResponse<ReferralDto> updateStatus(
            @PathVariable UUID referralId, @Valid @RequestBody UpdateReferralStatusRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(
                ReferralDto.from(referralService.updateStatus(principal, referralId, request.status(), request.convertedContactId())), "Status updated");
    }

    @PatchMapping("/{referralId}/reward")
    @PreAuthorize("hasAnyAuthority('REFERRAL:UPDATE:OWN','REFERRAL:UPDATE:TEAM','REFERRAL:UPDATE:DEPARTMENT','REFERRAL:UPDATE:ORGANIZATION')")
    public ApiResponse<ReferralDto> issueReward(@PathVariable UUID referralId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ReferralDto.from(referralService.issueReward(principal, referralId)), "Reward issued");
    }

    @DeleteMapping("/{referralId}")
    @PreAuthorize("hasAnyAuthority('REFERRAL:DELETE:OWN','REFERRAL:DELETE:TEAM','REFERRAL:DELETE:DEPARTMENT','REFERRAL:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID referralId, @AuthenticationPrincipal UserPrincipal principal) {
        referralService.delete(principal, referralId);
        return ApiResponse.ok(null, "Referral deleted");
    }
}
