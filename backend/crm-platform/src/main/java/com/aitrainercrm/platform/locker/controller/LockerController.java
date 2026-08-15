package com.aitrainercrm.platform.locker.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.locker.dto.CreateLockerRequest;
import com.aitrainercrm.platform.locker.dto.LockerDto;
import com.aitrainercrm.platform.locker.dto.UpdateLockerRequest;
import com.aitrainercrm.platform.locker.entity.Locker;
import com.aitrainercrm.platform.locker.service.LockerService;
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

/** No OWN scope on LOCKER (see LockerService's javadoc) - mirrors VendorController exactly. */
@RestController
@RequestMapping("/api/v1/lockers")
@RequiredArgsConstructor
public class LockerController {

    private final LockerService lockerService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('LOCKER:READ:TEAM','LOCKER:READ:DEPARTMENT','LOCKER:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<LockerDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<Locker> page = lockerService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(LockerDto::from).toList()));
    }

    @GetMapping("/{lockerId}")
    @PreAuthorize("hasAnyAuthority('LOCKER:READ:TEAM','LOCKER:READ:DEPARTMENT','LOCKER:READ:ORGANIZATION')")
    public ApiResponse<LockerDto> get(@PathVariable UUID lockerId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(LockerDto.from(lockerService.get(principal, lockerId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('LOCKER:CREATE:TEAM','LOCKER:CREATE:DEPARTMENT','LOCKER:CREATE:ORGANIZATION')")
    public ApiResponse<LockerDto> create(@Valid @RequestBody CreateLockerRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(LockerDto.from(lockerService.create(principal, request)), "Locker added");
    }

    @PutMapping("/{lockerId}")
    @PreAuthorize("hasAnyAuthority('LOCKER:UPDATE:TEAM','LOCKER:UPDATE:DEPARTMENT','LOCKER:UPDATE:ORGANIZATION')")
    public ApiResponse<LockerDto> update(
            @PathVariable UUID lockerId, @Valid @RequestBody UpdateLockerRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(LockerDto.from(lockerService.update(principal, lockerId, request)), "Locker updated");
    }

    @DeleteMapping("/{lockerId}")
    @PreAuthorize("hasAnyAuthority('LOCKER:DELETE:TEAM','LOCKER:DELETE:DEPARTMENT','LOCKER:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID lockerId, @AuthenticationPrincipal UserPrincipal principal) {
        lockerService.delete(principal, lockerId);
        return ApiResponse.ok(null, "Locker deleted");
    }
}
