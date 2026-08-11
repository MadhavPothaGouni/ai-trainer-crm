package com.aitrainercrm.platform.ticket.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.AssignOwnerRequest;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.ticket.dto.CreateTicketRequest;
import com.aitrainercrm.platform.ticket.dto.TicketDto;
import com.aitrainercrm.platform.ticket.dto.UpdateTicketRequest;
import com.aitrainercrm.platform.ticket.dto.UpdateTicketStatusRequest;
import com.aitrainercrm.platform.ticket.entity.Ticket;
import com.aitrainercrm.platform.ticket.service.TicketService;
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

/** Mirrors AccountController's shape exactly - see AccountController's own javadoc for the reasoning behind the coarse-@PreAuthorize-then-service-layer-record-check split. */
@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('TICKET:READ:OWN','TICKET:READ:TEAM','TICKET:READ:DEPARTMENT','TICKET:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<TicketDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<Ticket> page = ticketService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(TicketDto::from).toList()));
    }

    @GetMapping("/{ticketId}")
    @PreAuthorize("hasAnyAuthority('TICKET:READ:OWN','TICKET:READ:TEAM','TICKET:READ:DEPARTMENT','TICKET:READ:ORGANIZATION')")
    public ApiResponse<TicketDto> get(@PathVariable UUID ticketId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(TicketDto.from(ticketService.get(principal, ticketId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('TICKET:CREATE:OWN','TICKET:CREATE:TEAM','TICKET:CREATE:DEPARTMENT','TICKET:CREATE:ORGANIZATION')")
    public ApiResponse<TicketDto> create(@Valid @RequestBody CreateTicketRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(TicketDto.from(ticketService.create(principal, request)), "Ticket created");
    }

    @PutMapping("/{ticketId}")
    @PreAuthorize("hasAnyAuthority('TICKET:UPDATE:OWN','TICKET:UPDATE:TEAM','TICKET:UPDATE:DEPARTMENT','TICKET:UPDATE:ORGANIZATION')")
    public ApiResponse<TicketDto> update(
            @PathVariable UUID ticketId, @Valid @RequestBody UpdateTicketRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(TicketDto.from(ticketService.update(principal, ticketId, request)), "Ticket updated");
    }

    @PatchMapping("/{ticketId}/status")
    @PreAuthorize("hasAnyAuthority('TICKET:UPDATE:OWN','TICKET:UPDATE:TEAM','TICKET:UPDATE:DEPARTMENT','TICKET:UPDATE:ORGANIZATION')")
    public ApiResponse<TicketDto> updateStatus(
            @PathVariable UUID ticketId, @Valid @RequestBody UpdateTicketStatusRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(TicketDto.from(ticketService.updateStatus(principal, ticketId, request.status())), "Status updated");
    }

    @DeleteMapping("/{ticketId}")
    @PreAuthorize("hasAnyAuthority('TICKET:DELETE:OWN','TICKET:DELETE:TEAM','TICKET:DELETE:DEPARTMENT','TICKET:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID ticketId, @AuthenticationPrincipal UserPrincipal principal) {
        ticketService.delete(principal, ticketId);
        return ApiResponse.ok(null, "Ticket deleted");
    }

    @PatchMapping("/{ticketId}/owner")
    @PreAuthorize("hasAnyAuthority('TICKET:ASSIGN:OWN','TICKET:ASSIGN:TEAM','TICKET:ASSIGN:DEPARTMENT','TICKET:ASSIGN:ORGANIZATION')")
    public ApiResponse<TicketDto> assignOwner(
            @PathVariable UUID ticketId, @Valid @RequestBody AssignOwnerRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(TicketDto.from(ticketService.assignOwner(principal, ticketId, request.ownerId())), "Owner updated");
    }
}
