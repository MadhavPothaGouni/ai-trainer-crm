package com.aitrainercrm.platform.sla.service;

import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.notification.inbox.entity.Notification;
import com.aitrainercrm.platform.notification.inbox.service.NotificationService;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.sla.entity.SlaPolicy;
import com.aitrainercrm.platform.sla.entity.TicketSlaStatus;
import com.aitrainercrm.platform.sla.repository.SlaPolicyRepository;
import com.aitrainercrm.platform.sla.repository.TicketSlaStatusRepository;
import com.aitrainercrm.platform.ticket.entity.Ticket;
import com.aitrainercrm.platform.ticket.repository.TicketRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The SLA engine: given a {@link Ticket}, finds the org's active {@link SlaPolicy} for its
 * priority, tracks a {@link TicketSlaStatus} row against it, and escalates (via {@code
 * NotificationService#createSystem}) the first time either deadline is missed. Deliberately reads
 * {@link Ticket}'s own {@code status}/{@code resolvedAt}/{@code priority}/{@code createdAt} fields
 * directly rather than duplicating them - no separate "first response" timestamp exists anywhere
 * in this module; "responded" is defined as "status is no longer OPEN" and "resolved" is {@code
 * Ticket#getResolvedAt() != null}, both already true today with zero changes to {@link Ticket} or
 * {@code TicketService}. This module is purely additive: it has never once written to the
 * {@code tickets} table.
 *
 * <p>Two ways this gets triggered: a live call from {@link #getForTicket} (GET
 * /tickets/{id}/sla), which re-evaluates on every request so breach state shown to a user is
 * never staler than "as of this request," and the periodic {@link #sweep()}, which exists purely
 * so an escalation notification still fires even when nobody happens to be looking at the ticket.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SlaEvaluationService {

    private static final Permission.Resource TICKET_RESOURCE = Permission.Resource.TICKET;
    private static final List<Ticket.Status> TRACKED_STATUSES = List.of(Ticket.Status.OPEN, Ticket.Status.IN_PROGRESS);

    private final TicketRepository ticketRepository;
    private final SlaPolicyRepository slaPolicyRepository;
    private final TicketSlaStatusRepository ticketSlaStatusRepository;
    private final NotificationService notificationService;
    private final ScopeAuthorizationService scopeAuthorizationService;

    /**
     * Gated on TICKET:READ against the ticket's own ownerId - deliberately not a separate
     * SLA-specific permission. Seeing a ticket's SLA status shouldn't require anything beyond
     * already being able to see the ticket itself, the same reasoning {@code DashboardService}'s
     * read path piggybacks on {@code REPORT:READ} rather than inventing a redundant check (see
     * backend/crm-platform/README.md's module layout for `dashboard`).
     */
    @Transactional
    public Optional<TicketSlaStatus> getForTicket(UserPrincipal principal, UUID ticketId) {
        Ticket ticket = ticketRepository
                .findActiveByIdAndOrganizationId(ticketId, principal.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
        scopeAuthorizationService.assertCanAccess(principal, TICKET_RESOURCE, Permission.Action.READ, ticket.getOwnerId());
        return evaluate(ticket);
    }

    /**
     * Runs every {@code crm.sla.sweep-interval-ms} (default 5 minutes, see application.yml)
     * across every still-open ticket in every organization on the platform - genuinely
     * cross-tenant, unlike anything else scheduled in request-handling code. This is the first
     * real user of {@code @EnableScheduling} on {@code CrmPlatformApplication}, which has been
     * present since the application's very first commit with nothing actually scheduled until
     * now. Each ticket is evaluated inside its own try/catch so one bad row's exception can't
     * abort the rest of the sweep.
     */
    @Scheduled(fixedRateString = "${crm.sla.sweep-interval-ms:300000}")
    public void sweep() {
        List<Ticket> openTickets = ticketRepository.findByStatusInAndDeletedAtIsNull(TRACKED_STATUSES);
        for (Ticket ticket : openTickets) {
            try {
                evaluate(ticket);
            } catch (Exception e) {
                log.warn("SLA sweep failed for ticket {}: {}", ticket.getId(), e.toString());
            }
        }
    }

    /**
     * No active policy for this ticket's priority -> nothing tracked, returns empty (not an
     * error - most orgs won't have configured every priority). Otherwise find-or-creates the
     * ticket's {@link TicketSlaStatus} row (due dates computed once, from {@code
     * ticket.getCreatedAt()} plus the policy's targets, and never recomputed afterward even if
     * the policy's targets later change - see V20's migration comment), then checks both
     * deadlines. A deadline that flips from not-breached to breached fires exactly one escalation
     * notification, guarded by {@code escalatedAt} so re-evaluating the same already-breached
     * ticket (which the sweep does every few minutes for as long as it stays open) never sends a
     * second one.
     */
    @Transactional
    public Optional<TicketSlaStatus> evaluate(Ticket ticket) {
        Optional<SlaPolicy> policyOpt =
                slaPolicyRepository.findByOrganizationIdAndPriorityAndActiveTrue(ticket.getOrganizationId(), ticket.getPriority());
        if (policyOpt.isEmpty()) {
            return Optional.empty();
        }
        SlaPolicy policy = policyOpt.get();

        TicketSlaStatus status = ticketSlaStatusRepository
                .findByTicketId(ticket.getId())
                .orElseGet(() -> new TicketSlaStatus(
                        ticket.getOrganizationId(),
                        ticket.getId(),
                        policy.getId(),
                        ticket.getCreatedAt().plus(policy.getResponseTargetMinutes(), ChronoUnit.MINUTES),
                        ticket.getCreatedAt().plus(policy.getResolutionTargetMinutes(), ChronoUnit.MINUTES)));

        Instant now = Instant.now();
        boolean newlyBreached = false;

        boolean responded = ticket.getStatus() != Ticket.Status.OPEN;
        if (!responded && status.getResponseBreachedAt() == null && now.isAfter(status.getResponseDueAt())) {
            status.setResponseBreachedAt(now);
            newlyBreached = true;
        }

        boolean resolved = ticket.getResolvedAt() != null;
        if (!resolved && status.getResolutionBreachedAt() == null && now.isAfter(status.getResolutionDueAt())) {
            status.setResolutionBreachedAt(now);
            newlyBreached = true;
        }

        ticketSlaStatusRepository.save(status);

        if (newlyBreached && status.getEscalatedAt() == null && policy.getEscalateToUserId() != null) {
            escalate(ticket, policy, status);
        }

        return Optional.of(status);
    }

    private void escalate(Ticket ticket, SlaPolicy policy, TicketSlaStatus status) {
        notificationService.createSystem(
                ticket.getOrganizationId(),
                policy.getEscalateToUserId(),
                Notification.Type.ESCALATION,
                "SLA breach: " + ticket.getSubject(),
                "%s priority ticket has missed its SLA target.".formatted(ticket.getPriority()),
                Notification.RelatedToType.TICKET,
                ticket.getId());
        status.setEscalatedAt(Instant.now());
        ticketSlaStatusRepository.save(status);
    }
}
