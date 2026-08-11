package com.aitrainercrm.platform.activity.controller;

import com.aitrainercrm.platform.activity.dto.ActivityDto;
import com.aitrainercrm.platform.activity.dto.CreateActivityRequest;
import com.aitrainercrm.platform.activity.dto.UpdateActivityRequest;
import com.aitrainercrm.platform.activity.dto.UpdateActivityStatusRequest;
import com.aitrainercrm.platform.activity.entity.Activity;
import com.aitrainercrm.platform.activity.service.ActivityService;
import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.AssignOwnerRequest;
import com.aitrainercrm.platform.common.dto.PageResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Same coarse-then-fine authorization split as AccountController: {@code @PreAuthorize}
 * proves the caller holds *some* level of ACTIVITY access, ActivityService's
 * ScopeAuthorizationService calls decide whether they hold enough for *this*
 * activity (or, for list, which owners' activities they can see at all).
 */
@RestController
@RequestMapping("/api/v1/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    /** relatedToType/relatedToId together scope the list to one record's timeline (e.g. an Account detail page); omit both for a flat, scope-filtered list across every record. */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ACTIVITY:READ:OWN','ACTIVITY:READ:TEAM','ACTIVITY:READ:DEPARTMENT','ACTIVITY:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<ActivityDto>> list(
            @RequestParam(required = false) Activity.RelatedToType relatedToType,
            @RequestParam(required = false) UUID relatedToId,
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        Page<Activity> page = activityService.list(principal, relatedToType, relatedToId, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(ActivityDto::from).toList()));
    }

    @GetMapping("/{activityId}")
    @PreAuthorize("hasAnyAuthority('ACTIVITY:READ:OWN','ACTIVITY:READ:TEAM','ACTIVITY:READ:DEPARTMENT','ACTIVITY:READ:ORGANIZATION')")
    public ApiResponse<ActivityDto> get(@PathVariable UUID activityId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ActivityDto.from(activityService.get(principal, activityId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('ACTIVITY:CREATE:OWN','ACTIVITY:CREATE:TEAM','ACTIVITY:CREATE:DEPARTMENT','ACTIVITY:CREATE:ORGANIZATION')")
    public ApiResponse<ActivityDto> create(@Valid @RequestBody CreateActivityRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ActivityDto.from(activityService.create(principal, request)), "Activity created");
    }

    @PutMapping("/{activityId}")
    @PreAuthorize("hasAnyAuthority('ACTIVITY:UPDATE:OWN','ACTIVITY:UPDATE:TEAM','ACTIVITY:UPDATE:DEPARTMENT','ACTIVITY:UPDATE:ORGANIZATION')")
    public ApiResponse<ActivityDto> update(
            @PathVariable UUID activityId, @Valid @RequestBody UpdateActivityRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ActivityDto.from(activityService.update(principal, activityId, request)), "Activity updated");
    }

    @PatchMapping("/{activityId}/status")
    @PreAuthorize("hasAnyAuthority('ACTIVITY:UPDATE:OWN','ACTIVITY:UPDATE:TEAM','ACTIVITY:UPDATE:DEPARTMENT','ACTIVITY:UPDATE:ORGANIZATION')")
    public ApiResponse<ActivityDto> updateStatus(
            @PathVariable UUID activityId, @Valid @RequestBody UpdateActivityStatusRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ActivityDto.from(activityService.updateStatus(principal, activityId, request.status())), "Status updated");
    }

    @DeleteMapping("/{activityId}")
    @PreAuthorize("hasAnyAuthority('ACTIVITY:DELETE:OWN','ACTIVITY:DELETE:TEAM','ACTIVITY:DELETE:DEPARTMENT','ACTIVITY:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID activityId, @AuthenticationPrincipal UserPrincipal principal) {
        activityService.delete(principal, activityId);
        return ApiResponse.ok(null, "Activity deleted");
    }

    @PatchMapping("/{activityId}/owner")
    @PreAuthorize("hasAnyAuthority('ACTIVITY:ASSIGN:OWN','ACTIVITY:ASSIGN:TEAM','ACTIVITY:ASSIGN:DEPARTMENT','ACTIVITY:ASSIGN:ORGANIZATION')")
    public ApiResponse<ActivityDto> assignOwner(
            @PathVariable UUID activityId, @Valid @RequestBody AssignOwnerRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ActivityDto.from(activityService.assignOwner(principal, activityId, request.ownerId())), "Owner updated");
    }
}
