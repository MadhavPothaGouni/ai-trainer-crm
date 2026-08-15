package com.aitrainercrm.platform.noshow.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.noshow.dto.CreateNoShowRecordRequest;
import com.aitrainercrm.platform.noshow.dto.NoShowRecordDto;
import com.aitrainercrm.platform.noshow.dto.UpdateNoShowRecordRequest;
import com.aitrainercrm.platform.noshow.entity.NoShowRecord;
import com.aitrainercrm.platform.noshow.service.NoShowRecordService;
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

/** Mirrors ReferralController's shape, including the dedicated POST .../waive action endpoint instead of a PATCH .../status endpoint. */
@RestController
@RequestMapping("/api/v1/no-show-records")
@RequiredArgsConstructor
public class NoShowRecordController {

    private final NoShowRecordService noShowRecordService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('NO_SHOW_RECORD:READ:OWN','NO_SHOW_RECORD:READ:TEAM','NO_SHOW_RECORD:READ:DEPARTMENT','NO_SHOW_RECORD:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<NoShowRecordDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<NoShowRecord> page = noShowRecordService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(NoShowRecordDto::from).toList()));
    }

    @GetMapping("/{noShowRecordId}")
    @PreAuthorize("hasAnyAuthority('NO_SHOW_RECORD:READ:OWN','NO_SHOW_RECORD:READ:TEAM','NO_SHOW_RECORD:READ:DEPARTMENT','NO_SHOW_RECORD:READ:ORGANIZATION')")
    public ApiResponse<NoShowRecordDto> get(@PathVariable UUID noShowRecordId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(NoShowRecordDto.from(noShowRecordService.get(principal, noShowRecordId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('NO_SHOW_RECORD:CREATE:OWN','NO_SHOW_RECORD:CREATE:TEAM','NO_SHOW_RECORD:CREATE:DEPARTMENT','NO_SHOW_RECORD:CREATE:ORGANIZATION')")
    public ApiResponse<NoShowRecordDto> create(
            @Valid @RequestBody CreateNoShowRecordRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(NoShowRecordDto.from(noShowRecordService.create(principal, request)), "No-show record created");
    }

    @PutMapping("/{noShowRecordId}")
    @PreAuthorize("hasAnyAuthority('NO_SHOW_RECORD:UPDATE:OWN','NO_SHOW_RECORD:UPDATE:TEAM','NO_SHOW_RECORD:UPDATE:DEPARTMENT','NO_SHOW_RECORD:UPDATE:ORGANIZATION')")
    public ApiResponse<NoShowRecordDto> update(
            @PathVariable UUID noShowRecordId,
            @Valid @RequestBody UpdateNoShowRecordRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(NoShowRecordDto.from(noShowRecordService.update(principal, noShowRecordId, request)), "No-show record updated");
    }

    @PostMapping("/{noShowRecordId}/waive")
    @PreAuthorize("hasAnyAuthority('NO_SHOW_RECORD:UPDATE:OWN','NO_SHOW_RECORD:UPDATE:TEAM','NO_SHOW_RECORD:UPDATE:DEPARTMENT','NO_SHOW_RECORD:UPDATE:ORGANIZATION')")
    public ApiResponse<NoShowRecordDto> waive(@PathVariable UUID noShowRecordId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(NoShowRecordDto.from(noShowRecordService.waive(principal, noShowRecordId)), "Fee waived");
    }

    @DeleteMapping("/{noShowRecordId}")
    @PreAuthorize("hasAnyAuthority('NO_SHOW_RECORD:DELETE:OWN','NO_SHOW_RECORD:DELETE:TEAM','NO_SHOW_RECORD:DELETE:DEPARTMENT','NO_SHOW_RECORD:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID noShowRecordId, @AuthenticationPrincipal UserPrincipal principal) {
        noShowRecordService.delete(principal, noShowRecordId);
        return ApiResponse.ok(null, "No-show record deleted");
    }
}
