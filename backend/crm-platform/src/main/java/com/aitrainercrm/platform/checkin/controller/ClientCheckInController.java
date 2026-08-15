package com.aitrainercrm.platform.checkin.controller;

import com.aitrainercrm.platform.checkin.dto.ClientCheckInDto;
import com.aitrainercrm.platform.checkin.dto.CreateClientCheckInRequest;
import com.aitrainercrm.platform.checkin.dto.UpdateClientCheckInRequest;
import com.aitrainercrm.platform.checkin.dto.UpdateClientCheckInStatusRequest;
import com.aitrainercrm.platform.checkin.entity.ClientCheckIn;
import com.aitrainercrm.platform.checkin.service.ClientCheckInService;
import com.aitrainercrm.platform.common.dto.ApiResponse;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Mirrors TimeOffRequestController's shape exactly, including the separate PATCH .../status endpoint for checkedOutAt stamping. */
@RestController
@RequestMapping("/api/v1/client-check-ins")
@RequiredArgsConstructor
public class ClientCheckInController {

    private final ClientCheckInService clientCheckInService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('CLIENT_CHECK_IN:READ:OWN','CLIENT_CHECK_IN:READ:TEAM','CLIENT_CHECK_IN:READ:DEPARTMENT','CLIENT_CHECK_IN:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<ClientCheckInDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<ClientCheckIn> page = clientCheckInService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(ClientCheckInDto::from).toList()));
    }

    @GetMapping("/{clientCheckInId}")
    @PreAuthorize("hasAnyAuthority('CLIENT_CHECK_IN:READ:OWN','CLIENT_CHECK_IN:READ:TEAM','CLIENT_CHECK_IN:READ:DEPARTMENT','CLIENT_CHECK_IN:READ:ORGANIZATION')")
    public ApiResponse<ClientCheckInDto> get(@PathVariable UUID clientCheckInId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ClientCheckInDto.from(clientCheckInService.get(principal, clientCheckInId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('CLIENT_CHECK_IN:CREATE:OWN','CLIENT_CHECK_IN:CREATE:TEAM','CLIENT_CHECK_IN:CREATE:DEPARTMENT','CLIENT_CHECK_IN:CREATE:ORGANIZATION')")
    public ApiResponse<ClientCheckInDto> create(
            @Valid @RequestBody CreateClientCheckInRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ClientCheckInDto.from(clientCheckInService.create(principal, request)), "Check-in recorded");
    }

    @PutMapping("/{clientCheckInId}")
    @PreAuthorize("hasAnyAuthority('CLIENT_CHECK_IN:UPDATE:OWN','CLIENT_CHECK_IN:UPDATE:TEAM','CLIENT_CHECK_IN:UPDATE:DEPARTMENT','CLIENT_CHECK_IN:UPDATE:ORGANIZATION')")
    public ApiResponse<ClientCheckInDto> update(
            @PathVariable UUID clientCheckInId,
            @Valid @RequestBody UpdateClientCheckInRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ClientCheckInDto.from(clientCheckInService.update(principal, clientCheckInId, request)), "Check-in updated");
    }

    @PatchMapping("/{clientCheckInId}/status")
    @PreAuthorize("hasAnyAuthority('CLIENT_CHECK_IN:UPDATE:OWN','CLIENT_CHECK_IN:UPDATE:TEAM','CLIENT_CHECK_IN:UPDATE:DEPARTMENT','CLIENT_CHECK_IN:UPDATE:ORGANIZATION')")
    public ApiResponse<ClientCheckInDto> updateStatus(
            @PathVariable UUID clientCheckInId,
            @Valid @RequestBody UpdateClientCheckInStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(
                ClientCheckInDto.from(clientCheckInService.updateStatus(principal, clientCheckInId, request.status())), "Status updated");
    }

    @DeleteMapping("/{clientCheckInId}")
    @PreAuthorize("hasAnyAuthority('CLIENT_CHECK_IN:DELETE:OWN','CLIENT_CHECK_IN:DELETE:TEAM','CLIENT_CHECK_IN:DELETE:DEPARTMENT','CLIENT_CHECK_IN:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID clientCheckInId, @AuthenticationPrincipal UserPrincipal principal) {
        clientCheckInService.delete(principal, clientCheckInId);
        return ApiResponse.ok(null, "Check-in deleted");
    }
}
