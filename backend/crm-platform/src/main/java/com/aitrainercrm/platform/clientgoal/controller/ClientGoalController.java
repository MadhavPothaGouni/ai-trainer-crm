package com.aitrainercrm.platform.clientgoal.controller;

import com.aitrainercrm.platform.clientgoal.dto.ClientGoalDto;
import com.aitrainercrm.platform.clientgoal.dto.CreateClientGoalRequest;
import com.aitrainercrm.platform.clientgoal.dto.UpdateClientGoalRequest;
import com.aitrainercrm.platform.clientgoal.dto.UpdateClientGoalStatusRequest;
import com.aitrainercrm.platform.clientgoal.entity.ClientGoal;
import com.aitrainercrm.platform.clientgoal.service.ClientGoalService;
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

/** Mirrors ContractController's shape exactly - see TicketController's own javadoc for the reasoning behind the coarse-@PreAuthorize-then-service-layer-record-check split. */
@RestController
@RequestMapping("/api/v1/client-goals")
@RequiredArgsConstructor
public class ClientGoalController {

    private final ClientGoalService clientGoalService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('CLIENT_GOAL:READ:OWN','CLIENT_GOAL:READ:TEAM','CLIENT_GOAL:READ:DEPARTMENT','CLIENT_GOAL:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<ClientGoalDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<ClientGoal> page = clientGoalService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(ClientGoalDto::from).toList()));
    }

    @GetMapping("/{clientGoalId}")
    @PreAuthorize("hasAnyAuthority('CLIENT_GOAL:READ:OWN','CLIENT_GOAL:READ:TEAM','CLIENT_GOAL:READ:DEPARTMENT','CLIENT_GOAL:READ:ORGANIZATION')")
    public ApiResponse<ClientGoalDto> get(@PathVariable UUID clientGoalId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ClientGoalDto.from(clientGoalService.get(principal, clientGoalId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('CLIENT_GOAL:CREATE:OWN','CLIENT_GOAL:CREATE:TEAM','CLIENT_GOAL:CREATE:DEPARTMENT','CLIENT_GOAL:CREATE:ORGANIZATION')")
    public ApiResponse<ClientGoalDto> create(@Valid @RequestBody CreateClientGoalRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ClientGoalDto.from(clientGoalService.create(principal, request)), "Client goal created");
    }

    @PutMapping("/{clientGoalId}")
    @PreAuthorize("hasAnyAuthority('CLIENT_GOAL:UPDATE:OWN','CLIENT_GOAL:UPDATE:TEAM','CLIENT_GOAL:UPDATE:DEPARTMENT','CLIENT_GOAL:UPDATE:ORGANIZATION')")
    public ApiResponse<ClientGoalDto> update(
            @PathVariable UUID clientGoalId, @Valid @RequestBody UpdateClientGoalRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ClientGoalDto.from(clientGoalService.update(principal, clientGoalId, request)), "Client goal updated");
    }

    @PatchMapping("/{clientGoalId}/status")
    @PreAuthorize("hasAnyAuthority('CLIENT_GOAL:UPDATE:OWN','CLIENT_GOAL:UPDATE:TEAM','CLIENT_GOAL:UPDATE:DEPARTMENT','CLIENT_GOAL:UPDATE:ORGANIZATION')")
    public ApiResponse<ClientGoalDto> updateStatus(
            @PathVariable UUID clientGoalId, @Valid @RequestBody UpdateClientGoalStatusRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ClientGoalDto.from(clientGoalService.updateStatus(principal, clientGoalId, request.status())), "Status updated");
    }

    @DeleteMapping("/{clientGoalId}")
    @PreAuthorize("hasAnyAuthority('CLIENT_GOAL:DELETE:OWN','CLIENT_GOAL:DELETE:TEAM','CLIENT_GOAL:DELETE:DEPARTMENT','CLIENT_GOAL:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID clientGoalId, @AuthenticationPrincipal UserPrincipal principal) {
        clientGoalService.delete(principal, clientGoalId);
        return ApiResponse.ok(null, "Client goal deleted");
    }
}
