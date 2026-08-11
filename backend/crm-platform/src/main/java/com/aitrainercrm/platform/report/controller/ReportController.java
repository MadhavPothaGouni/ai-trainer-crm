package com.aitrainercrm.platform.report.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.report.dto.LeadFunnelStageDto;
import com.aitrainercrm.platform.report.dto.PipelineStageSummaryDto;
import com.aitrainercrm.platform.report.dto.RepLeaderboardEntryDto;
import com.aitrainercrm.platform.report.service.ReportService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Three read-only aggregation endpoints - REPORT is seeded in
 * {@code V2__seed_permission_catalog.sql} with OWN/TEAM/ORGANIZATION scope
 * (no DEPARTMENT) and CREATE/READ/UPDATE/DELETE/MANAGE actions, but only
 * READ is wired up here; the others exist in the catalog for a future
 * saved-report/dashboard-builder feature, not this pass. REPORT isn't one
 * of RoleService's "core CRM resources," so the default MEMBER role holds
 * none of it - only OWNER/ADMIN (and any custom role granted it) see these
 * pages, same as Products.
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/pipeline-by-stage")
    @PreAuthorize("hasAnyAuthority('REPORT:READ:OWN','REPORT:READ:TEAM','REPORT:READ:ORGANIZATION')")
    public ApiResponse<List<PipelineStageSummaryDto>> pipelineByStage(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(reportService.pipelineByStage(principal));
    }

    @GetMapping("/lead-funnel")
    @PreAuthorize("hasAnyAuthority('REPORT:READ:OWN','REPORT:READ:TEAM','REPORT:READ:ORGANIZATION')")
    public ApiResponse<List<LeadFunnelStageDto>> leadFunnel(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(reportService.leadFunnel(principal));
    }

    @GetMapping("/leaderboard")
    @PreAuthorize("hasAnyAuthority('REPORT:READ:OWN','REPORT:READ:TEAM','REPORT:READ:ORGANIZATION')")
    public ApiResponse<List<RepLeaderboardEntryDto>> leaderboard(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(reportService.repLeaderboard(principal));
    }
}
