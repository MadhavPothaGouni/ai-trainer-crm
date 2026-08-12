package com.aitrainercrm.platform.forecast.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.forecast.dto.PipelineSnapshotDto;
import com.aitrainercrm.platform.forecast.dto.PipelineTrendPointDto;
import com.aitrainercrm.platform.forecast.service.PipelineSnapshotService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Two read-only endpoints, no create/update/delete - {@link PipelineSnapshotService#captureDaily}
 * is the module's only writer. Gated on {@code REPORT:READ} rather than a resource of its own,
 * same shape {@code ReportController} uses - see {@code PipelineSnapshotService}'s javadoc.
 */
@RestController
@RequestMapping("/api/v1/forecast")
@RequiredArgsConstructor
public class PipelineSnapshotController {

    private final PipelineSnapshotService pipelineSnapshotService;

    @GetMapping("/snapshots")
    @PreAuthorize("hasAnyAuthority('REPORT:READ:OWN','REPORT:READ:TEAM','REPORT:READ:ORGANIZATION')")
    public ApiResponse<List<PipelineSnapshotDto>> snapshots(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(pipelineSnapshotService.listSnapshots(principal, from, to));
    }

    @GetMapping("/trend")
    @PreAuthorize("hasAnyAuthority('REPORT:READ:OWN','REPORT:READ:TEAM','REPORT:READ:ORGANIZATION')")
    public ApiResponse<List<PipelineTrendPointDto>> trend(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(pipelineSnapshotService.trend(principal, from, to));
    }
}
