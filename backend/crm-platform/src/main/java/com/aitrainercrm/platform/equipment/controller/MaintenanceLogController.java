package com.aitrainercrm.platform.equipment.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.equipment.dto.CreateMaintenanceLogRequest;
import com.aitrainercrm.platform.equipment.dto.MaintenanceLogDto;
import com.aitrainercrm.platform.equipment.dto.UpdateMaintenanceLogRequest;
import com.aitrainercrm.platform.equipment.entity.MaintenanceLog;
import com.aitrainercrm.platform.equipment.service.MaintenanceLogService;
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

/** Mirrors ClientGoalController's shape, minus the PATCH .../status endpoint - see MaintenanceLog's javadoc for why there's no status to transition. */
@RestController
@RequestMapping("/api/v1/maintenance-logs")
@RequiredArgsConstructor
public class MaintenanceLogController {

    private final MaintenanceLogService maintenanceLogService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('MAINTENANCE_LOG:READ:OWN','MAINTENANCE_LOG:READ:TEAM','MAINTENANCE_LOG:READ:DEPARTMENT','MAINTENANCE_LOG:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<MaintenanceLogDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<MaintenanceLog> page = maintenanceLogService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(MaintenanceLogDto::from).toList()));
    }

    @GetMapping("/{maintenanceLogId}")
    @PreAuthorize("hasAnyAuthority('MAINTENANCE_LOG:READ:OWN','MAINTENANCE_LOG:READ:TEAM','MAINTENANCE_LOG:READ:DEPARTMENT','MAINTENANCE_LOG:READ:ORGANIZATION')")
    public ApiResponse<MaintenanceLogDto> get(@PathVariable UUID maintenanceLogId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(MaintenanceLogDto.from(maintenanceLogService.get(principal, maintenanceLogId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('MAINTENANCE_LOG:CREATE:OWN','MAINTENANCE_LOG:CREATE:TEAM','MAINTENANCE_LOG:CREATE:DEPARTMENT','MAINTENANCE_LOG:CREATE:ORGANIZATION')")
    public ApiResponse<MaintenanceLogDto> create(
            @Valid @RequestBody CreateMaintenanceLogRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(MaintenanceLogDto.from(maintenanceLogService.create(principal, request)), "Maintenance log created");
    }

    @PutMapping("/{maintenanceLogId}")
    @PreAuthorize("hasAnyAuthority('MAINTENANCE_LOG:UPDATE:OWN','MAINTENANCE_LOG:UPDATE:TEAM','MAINTENANCE_LOG:UPDATE:DEPARTMENT','MAINTENANCE_LOG:UPDATE:ORGANIZATION')")
    public ApiResponse<MaintenanceLogDto> update(
            @PathVariable UUID maintenanceLogId,
            @Valid @RequestBody UpdateMaintenanceLogRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(MaintenanceLogDto.from(maintenanceLogService.update(principal, maintenanceLogId, request)), "Maintenance log updated");
    }

    @DeleteMapping("/{maintenanceLogId}")
    @PreAuthorize("hasAnyAuthority('MAINTENANCE_LOG:DELETE:OWN','MAINTENANCE_LOG:DELETE:TEAM','MAINTENANCE_LOG:DELETE:DEPARTMENT','MAINTENANCE_LOG:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID maintenanceLogId, @AuthenticationPrincipal UserPrincipal principal) {
        maintenanceLogService.delete(principal, maintenanceLogId);
        return ApiResponse.ok(null, "Maintenance log deleted");
    }
}
