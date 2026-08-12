package com.aitrainercrm.platform.sla.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.sla.dto.TicketSlaStatusDto;
import com.aitrainercrm.platform.sla.entity.TicketSlaStatus;
import com.aitrainercrm.platform.sla.service.SlaEvaluationService;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * No {@code @PreAuthorize} here - see {@code SlaEvaluationService#getForTicket}'s javadoc. The
 * real gate is the {@code TICKET:READ} scope check that method runs internally against the
 * ticket being asked about, same "authorization lives inside the service, not the controller"
 * shape {@code NotificationController} uses (for an entirely different reason - see its own
 * javadoc). A separate controller/path from {@code TicketController} rather than nesting under
 * {@code /tickets/{id}/sla} there, so this module never has to touch {@code TicketController} at
 * all - same "stay fully additive" reasoning {@code SlaEvaluationService}'s own javadoc gives for
 * never writing to the {@code tickets} table.
 */
@RestController
@RequestMapping("/api/v1/ticket-sla")
@RequiredArgsConstructor
public class TicketSlaController {

    private final SlaEvaluationService slaEvaluationService;

    /** {@code data} is null when no active SLA policy covers this ticket's current priority - not an error, just "nothing tracked here." */
    @GetMapping("/{ticketId}")
    public ApiResponse<TicketSlaStatusDto> get(@PathVariable UUID ticketId, @AuthenticationPrincipal UserPrincipal principal) {
        Optional<TicketSlaStatus> status = slaEvaluationService.getForTicket(principal, ticketId);
        if (status.isEmpty()) {
            return ApiResponse.ok(null, "No active SLA policy covers this ticket's priority");
        }
        return ApiResponse.ok(TicketSlaStatusDto.from(status.get()));
    }
}
