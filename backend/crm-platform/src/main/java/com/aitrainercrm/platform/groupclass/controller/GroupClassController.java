package com.aitrainercrm.platform.groupclass.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.groupclass.dto.CreateGroupClassRequest;
import com.aitrainercrm.platform.groupclass.dto.GroupClassDto;
import com.aitrainercrm.platform.groupclass.dto.UpdateGroupClassRequest;
import com.aitrainercrm.platform.groupclass.entity.GroupClass;
import com.aitrainercrm.platform.groupclass.service.GroupClassService;
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

/** No OWN scope on GROUP_CLASS (see GroupClassService's javadoc) - mirrors ProductController/MembershipPlanController exactly. */
@RestController
@RequestMapping("/api/v1/group-classes")
@RequiredArgsConstructor
public class GroupClassController {

    private final GroupClassService groupClassService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('GROUP_CLASS:READ:TEAM','GROUP_CLASS:READ:DEPARTMENT','GROUP_CLASS:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<GroupClassDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<GroupClass> page = groupClassService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(GroupClassDto::from).toList()));
    }

    @GetMapping("/{groupClassId}")
    @PreAuthorize("hasAnyAuthority('GROUP_CLASS:READ:TEAM','GROUP_CLASS:READ:DEPARTMENT','GROUP_CLASS:READ:ORGANIZATION')")
    public ApiResponse<GroupClassDto> get(@PathVariable UUID groupClassId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(GroupClassDto.from(groupClassService.get(principal, groupClassId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('GROUP_CLASS:CREATE:TEAM','GROUP_CLASS:CREATE:DEPARTMENT','GROUP_CLASS:CREATE:ORGANIZATION')")
    public ApiResponse<GroupClassDto> create(@Valid @RequestBody CreateGroupClassRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(GroupClassDto.from(groupClassService.create(principal, request)), "Group class created");
    }

    @PutMapping("/{groupClassId}")
    @PreAuthorize("hasAnyAuthority('GROUP_CLASS:UPDATE:TEAM','GROUP_CLASS:UPDATE:DEPARTMENT','GROUP_CLASS:UPDATE:ORGANIZATION')")
    public ApiResponse<GroupClassDto> update(
            @PathVariable UUID groupClassId, @Valid @RequestBody UpdateGroupClassRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(GroupClassDto.from(groupClassService.update(principal, groupClassId, request)), "Group class updated");
    }

    @DeleteMapping("/{groupClassId}")
    @PreAuthorize("hasAnyAuthority('GROUP_CLASS:DELETE:TEAM','GROUP_CLASS:DELETE:DEPARTMENT','GROUP_CLASS:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID groupClassId, @AuthenticationPrincipal UserPrincipal principal) {
        groupClassService.delete(principal, groupClassId);
        return ApiResponse.ok(null, "Group class deleted");
    }
}
