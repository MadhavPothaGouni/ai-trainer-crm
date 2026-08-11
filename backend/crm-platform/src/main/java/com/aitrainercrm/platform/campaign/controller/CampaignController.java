package com.aitrainercrm.platform.campaign.controller;

import com.aitrainercrm.platform.campaign.dto.AddCampaignMemberRequest;
import com.aitrainercrm.platform.campaign.dto.CampaignDto;
import com.aitrainercrm.platform.campaign.dto.CampaignMemberDto;
import com.aitrainercrm.platform.campaign.dto.CampaignStatsDto;
import com.aitrainercrm.platform.campaign.dto.CreateCampaignRequest;
import com.aitrainercrm.platform.campaign.dto.UpdateCampaignMemberStatusRequest;
import com.aitrainercrm.platform.campaign.dto.UpdateCampaignRequest;
import com.aitrainercrm.platform.campaign.dto.UpdateCampaignStatusRequest;
import com.aitrainercrm.platform.campaign.entity.Campaign;
import com.aitrainercrm.platform.campaign.service.CampaignService;
import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

/** No OWN scope and no APPROVE action on CAMPAIGN (see CampaignService's javadoc) - every @PreAuthorize here only lists TEAM/DEPARTMENT/ORGANIZATION. */
@RestController
@RequestMapping("/api/v1/campaigns")
@RequiredArgsConstructor
public class CampaignController {

    private final CampaignService campaignService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('CAMPAIGN:READ:TEAM','CAMPAIGN:READ:DEPARTMENT','CAMPAIGN:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<CampaignDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<Campaign> page = campaignService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(CampaignDto::from).toList()));
    }

    /**
     * CAMPAIGN:EXPORT-gated, deliberately a distinct permission from
     * CAMPAIGN:READ - a role can list/view campaigns without necessarily
     * being allowed to bulk-export them. Returns a raw CSV download, not an
     * {@code ApiResponse} envelope - a file download isn't a JSON API
     * response, so wrapping it the normal way would just add a layer the
     * browser's download handling has to look past.
     */
    @GetMapping("/export")
    @PreAuthorize("hasAnyAuthority('CAMPAIGN:EXPORT:TEAM','CAMPAIGN:EXPORT:DEPARTMENT','CAMPAIGN:EXPORT:ORGANIZATION')")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal UserPrincipal principal) {
        byte[] csv = campaignService.exportCsv(principal);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("campaigns.csv").build().toString())
                .body(csv);
    }

    @GetMapping("/{campaignId}")
    @PreAuthorize("hasAnyAuthority('CAMPAIGN:READ:TEAM','CAMPAIGN:READ:DEPARTMENT','CAMPAIGN:READ:ORGANIZATION')")
    public ApiResponse<CampaignDto> get(@PathVariable UUID campaignId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(CampaignDto.from(campaignService.get(principal, campaignId)));
    }

    @GetMapping("/{campaignId}/stats")
    @PreAuthorize("hasAnyAuthority('CAMPAIGN:READ:TEAM','CAMPAIGN:READ:DEPARTMENT','CAMPAIGN:READ:ORGANIZATION')")
    public ApiResponse<CampaignStatsDto> stats(@PathVariable UUID campaignId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(campaignService.getStats(principal, campaignId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('CAMPAIGN:CREATE:TEAM','CAMPAIGN:CREATE:DEPARTMENT','CAMPAIGN:CREATE:ORGANIZATION')")
    public ApiResponse<CampaignDto> create(@Valid @RequestBody CreateCampaignRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(CampaignDto.from(campaignService.create(principal, request)), "Campaign created");
    }

    @PutMapping("/{campaignId}")
    @PreAuthorize("hasAnyAuthority('CAMPAIGN:UPDATE:TEAM','CAMPAIGN:UPDATE:DEPARTMENT','CAMPAIGN:UPDATE:ORGANIZATION')")
    public ApiResponse<CampaignDto> update(
            @PathVariable UUID campaignId, @Valid @RequestBody UpdateCampaignRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(CampaignDto.from(campaignService.update(principal, campaignId, request)), "Campaign updated");
    }

    @PatchMapping("/{campaignId}/status")
    @PreAuthorize("hasAnyAuthority('CAMPAIGN:UPDATE:TEAM','CAMPAIGN:UPDATE:DEPARTMENT','CAMPAIGN:UPDATE:ORGANIZATION')")
    public ApiResponse<CampaignDto> updateStatus(
            @PathVariable UUID campaignId, @Valid @RequestBody UpdateCampaignStatusRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(CampaignDto.from(campaignService.updateStatus(principal, campaignId, request.status())), "Status updated");
    }

    @DeleteMapping("/{campaignId}")
    @PreAuthorize("hasAnyAuthority('CAMPAIGN:DELETE:TEAM','CAMPAIGN:DELETE:DEPARTMENT','CAMPAIGN:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID campaignId, @AuthenticationPrincipal UserPrincipal principal) {
        campaignService.delete(principal, campaignId);
        return ApiResponse.ok(null, "Campaign deleted");
    }

    @GetMapping("/{campaignId}/members")
    @PreAuthorize("hasAnyAuthority('CAMPAIGN:READ:TEAM','CAMPAIGN:READ:DEPARTMENT','CAMPAIGN:READ:ORGANIZATION')")
    public ApiResponse<java.util.List<CampaignMemberDto>> listMembers(
            @PathVariable UUID campaignId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(campaignService.getMembers(principal, campaignId).stream().map(CampaignMemberDto::from).toList());
    }

    @PostMapping("/{campaignId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('CAMPAIGN:UPDATE:TEAM','CAMPAIGN:UPDATE:DEPARTMENT','CAMPAIGN:UPDATE:ORGANIZATION')")
    public ApiResponse<CampaignMemberDto> addMember(
            @PathVariable UUID campaignId, @Valid @RequestBody AddCampaignMemberRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(CampaignMemberDto.from(campaignService.addMember(principal, campaignId, request)), "Member added");
    }

    @PatchMapping("/{campaignId}/members/{memberId}/status")
    @PreAuthorize("hasAnyAuthority('CAMPAIGN:UPDATE:TEAM','CAMPAIGN:UPDATE:DEPARTMENT','CAMPAIGN:UPDATE:ORGANIZATION')")
    public ApiResponse<CampaignMemberDto> updateMemberStatus(
            @PathVariable UUID campaignId, @PathVariable UUID memberId,
            @Valid @RequestBody UpdateCampaignMemberStatusRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(
                CampaignMemberDto.from(campaignService.updateMemberStatus(principal, campaignId, memberId, request.status())),
                "Member status updated");
    }

    @DeleteMapping("/{campaignId}/members/{memberId}")
    @PreAuthorize("hasAnyAuthority('CAMPAIGN:UPDATE:TEAM','CAMPAIGN:UPDATE:DEPARTMENT','CAMPAIGN:UPDATE:ORGANIZATION')")
    public ApiResponse<Void> removeMember(
            @PathVariable UUID campaignId, @PathVariable UUID memberId, @AuthenticationPrincipal UserPrincipal principal) {
        campaignService.removeMember(principal, campaignId, memberId);
        return ApiResponse.ok(null, "Member removed");
    }
}
