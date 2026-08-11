package com.aitrainercrm.platform.dashboard.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.dashboard.dto.CreateDashboardRequest;
import com.aitrainercrm.platform.dashboard.dto.CreateDashboardWidgetRequest;
import com.aitrainercrm.platform.dashboard.dto.DashboardDataDto;
import com.aitrainercrm.platform.dashboard.dto.DashboardDto;
import com.aitrainercrm.platform.dashboard.dto.DashboardWidgetDto;
import com.aitrainercrm.platform.dashboard.dto.UpdateDashboardRequest;
import com.aitrainercrm.platform.dashboard.dto.UpdateDashboardWidgetRequest;
import com.aitrainercrm.platform.dashboard.entity.Dashboard;
import com.aitrainercrm.platform.dashboard.service.DashboardService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
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

/**
 * DASHBOARD was seeded in V2 at OWN/TEAM/ORGANIZATION scope (no
 * DEPARTMENT) with CREATE/READ/UPDATE/DELETE/MANAGE - the same group as
 * WORKFLOW/REPORT. MANAGE gates {@link #setDefault}, the one action here
 * that's more than a plain field edit. Widget CRUD rides on the parent
 * dashboard's UPDATE authority rather than getting its own resource -
 * widgets aren't separately permissioned in the catalog, they're just
 * dashboard composition.
 */
@RestController
@RequestMapping("/api/v1/dashboards")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('DASHBOARD:READ:OWN','DASHBOARD:READ:TEAM','DASHBOARD:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<DashboardDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<Dashboard> page = dashboardService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(
                page, page.getContent().stream().map(dashboard -> DashboardDto.from(dashboard, List.of())).toList()));
    }

    @GetMapping("/{dashboardId}")
    @PreAuthorize("hasAnyAuthority('DASHBOARD:READ:OWN','DASHBOARD:READ:TEAM','DASHBOARD:READ:ORGANIZATION')")
    public ApiResponse<DashboardDto> get(@PathVariable UUID dashboardId, @AuthenticationPrincipal UserPrincipal principal) {
        Dashboard dashboard = dashboardService.get(principal, dashboardId);
        return ApiResponse.ok(DashboardDto.from(dashboard, dashboardService.listWidgets(principal, dashboardId)));
    }

    @GetMapping("/{dashboardId}/data")
    @PreAuthorize("hasAnyAuthority('DASHBOARD:READ:OWN','DASHBOARD:READ:TEAM','DASHBOARD:READ:ORGANIZATION')")
    public ApiResponse<DashboardDataDto> getData(@PathVariable UUID dashboardId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(dashboardService.getData(principal, dashboardId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('DASHBOARD:CREATE:OWN','DASHBOARD:CREATE:TEAM','DASHBOARD:CREATE:ORGANIZATION')")
    public ApiResponse<DashboardDto> create(@Valid @RequestBody CreateDashboardRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        Dashboard dashboard = dashboardService.create(principal, request);
        return ApiResponse.ok(DashboardDto.from(dashboard, List.of()), "Dashboard created");
    }

    @PutMapping("/{dashboardId}")
    @PreAuthorize("hasAnyAuthority('DASHBOARD:UPDATE:OWN','DASHBOARD:UPDATE:TEAM','DASHBOARD:UPDATE:ORGANIZATION')")
    public ApiResponse<DashboardDto> update(
            @PathVariable UUID dashboardId, @Valid @RequestBody UpdateDashboardRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        Dashboard dashboard = dashboardService.update(principal, dashboardId, request);
        return ApiResponse.ok(DashboardDto.from(dashboard, dashboardService.listWidgets(principal, dashboardId)), "Dashboard updated");
    }

    @PostMapping("/{dashboardId}/default")
    @PreAuthorize("hasAnyAuthority('DASHBOARD:MANAGE:OWN','DASHBOARD:MANAGE:TEAM','DASHBOARD:MANAGE:ORGANIZATION')")
    public ApiResponse<DashboardDto> setDefault(@PathVariable UUID dashboardId, @AuthenticationPrincipal UserPrincipal principal) {
        Dashboard dashboard = dashboardService.setDefault(principal, dashboardId);
        return ApiResponse.ok(DashboardDto.from(dashboard, dashboardService.listWidgets(principal, dashboardId)), "Default dashboard updated");
    }

    @DeleteMapping("/{dashboardId}")
    @PreAuthorize("hasAnyAuthority('DASHBOARD:DELETE:OWN','DASHBOARD:DELETE:TEAM','DASHBOARD:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID dashboardId, @AuthenticationPrincipal UserPrincipal principal) {
        dashboardService.delete(principal, dashboardId);
        return ApiResponse.ok(null, "Dashboard deleted");
    }

    @PostMapping("/{dashboardId}/widgets")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('DASHBOARD:UPDATE:OWN','DASHBOARD:UPDATE:TEAM','DASHBOARD:UPDATE:ORGANIZATION')")
    public ApiResponse<DashboardWidgetDto> addWidget(
            @PathVariable UUID dashboardId,
            @Valid @RequestBody CreateDashboardWidgetRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(DashboardWidgetDto.from(dashboardService.addWidget(principal, dashboardId, request)), "Widget added");
    }

    @PutMapping("/{dashboardId}/widgets/{widgetId}")
    @PreAuthorize("hasAnyAuthority('DASHBOARD:UPDATE:OWN','DASHBOARD:UPDATE:TEAM','DASHBOARD:UPDATE:ORGANIZATION')")
    public ApiResponse<DashboardWidgetDto> updateWidget(
            @PathVariable UUID dashboardId,
            @PathVariable UUID widgetId,
            @Valid @RequestBody UpdateDashboardWidgetRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(
                DashboardWidgetDto.from(dashboardService.updateWidget(principal, dashboardId, widgetId, request)), "Widget updated");
    }

    @DeleteMapping("/{dashboardId}/widgets/{widgetId}")
    @PreAuthorize("hasAnyAuthority('DASHBOARD:UPDATE:OWN','DASHBOARD:UPDATE:TEAM','DASHBOARD:UPDATE:ORGANIZATION')")
    public ApiResponse<Void> removeWidget(
            @PathVariable UUID dashboardId, @PathVariable UUID widgetId, @AuthenticationPrincipal UserPrincipal principal) {
        dashboardService.removeWidget(principal, dashboardId, widgetId);
        return ApiResponse.ok(null, "Widget removed");
    }
}
