package com.aitrainercrm.platform.sla.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.notification.inbox.entity.Notification;
import com.aitrainercrm.platform.notification.inbox.service.NotificationService;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.sla.entity.SlaPolicy;
import com.aitrainercrm.platform.sla.entity.TicketSlaStatus;
import com.aitrainercrm.platform.sla.repository.SlaPolicyRepository;
import com.aitrainercrm.platform.sla.repository.TicketSlaStatusRepository;
import com.aitrainercrm.platform.ticket.entity.Ticket;
import com.aitrainercrm.platform.ticket.repository.TicketRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Every dependency mocked - no Spring context, no database, no real waiting for a deadline to
 * pass in wall-clock time (the one thing an HTTP integration test structurally can't do quickly
 * for this module, since a policy's minimum target is one minute). Ticket.createdAt is set
 * directly via its Lombok setter to a time already past a short target, which is enough to
 * exercise every branch of {@code SlaEvaluationService#evaluate} without ever sleeping.
 * {@code #getForTicket} (the scope-checked, principal-aware entry point) isn't covered here - see
 * {@code SlaEscalationIntegrationTest} for that, plus policy CRUD and the "not tracked" read path.
 */
@ExtendWith(MockitoExtension.class)
class SlaEvaluationServiceTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private SlaPolicyRepository slaPolicyRepository;
    @Mock private TicketSlaStatusRepository ticketSlaStatusRepository;
    @Mock private NotificationService notificationService;
    @Mock private ScopeAuthorizationService scopeAuthorizationService;

    private SlaEvaluationService slaEvaluationService;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        slaEvaluationService = new SlaEvaluationService(
                ticketRepository, slaPolicyRepository, ticketSlaStatusRepository, notificationService, scopeAuthorizationService);
    }

    @Test
    void evaluate_noActivePolicyForPriority_returnsEmptyAndTracksNothing() {
        Ticket ticket = openTicket(Instant.now());
        when(slaPolicyRepository.findByOrganizationIdAndPriorityAndActiveTrue(organizationId, Ticket.Priority.HIGH))
                .thenReturn(Optional.empty());

        Optional<TicketSlaStatus> result = slaEvaluationService.evaluate(ticket);

        assertThat(result).isEmpty();
        verify(ticketSlaStatusRepository, never()).save(any());
        verifyNoEscalation();
    }

    @Test
    void evaluate_stillWithinBothTargets_recordsStatusWithNoBreach() {
        Ticket ticket = openTicket(Instant.now());
        SlaPolicy policy = policy(60, 240, null);
        stubPolicyLookup(policy);
        stubNoExistingStatus(ticket);

        Optional<TicketSlaStatus> result = slaEvaluationService.evaluate(ticket);

        assertThat(result).isPresent();
        assertThat(result.get().isResponseBreached()).isFalse();
        assertThat(result.get().isResolutionBreached()).isFalse();
        assertThat(result.get().isEscalated()).isFalse();
        verifyNoEscalation();
    }

    @Test
    void evaluate_responseDeadlinePassedWhileStillOpen_marksResponseBreachedAndEscalatesOnce() {
        UUID escalateToUserId = UUID.randomUUID();
        Ticket ticket = openTicket(Instant.now().minus(10, ChronoUnit.MINUTES));
        SlaPolicy policy = policy(1, 240, escalateToUserId);
        stubPolicyLookup(policy);
        stubNoExistingStatus(ticket);

        Optional<TicketSlaStatus> result = slaEvaluationService.evaluate(ticket);

        assertThat(result).isPresent();
        assertThat(result.get().isResponseBreached()).isTrue();
        assertThat(result.get().isResolutionBreached()).isFalse();
        assertThat(result.get().isEscalated()).isTrue();
        verify(notificationService)
                .createSystem(
                        eq(organizationId),
                        eq(escalateToUserId),
                        eq(Notification.Type.ESCALATION),
                        anyString(),
                        anyString(),
                        eq(Notification.RelatedToType.TICKET),
                        eq(ticket.getId()));
    }

    @Test
    void evaluate_resolutionDeadlinePassedButAlreadyResponded_onlyMarksResolutionBreached() {
        Ticket ticket = openTicket(Instant.now().minus(300, ChronoUnit.MINUTES));
        ticket.setStatus(Ticket.Status.IN_PROGRESS); // already responded - no response breach possible
        SlaPolicy policy = policy(60, 120, UUID.randomUUID());
        stubPolicyLookup(policy);
        stubNoExistingStatus(ticket);

        Optional<TicketSlaStatus> result = slaEvaluationService.evaluate(ticket);

        assertThat(result).isPresent();
        assertThat(result.get().isResponseBreached()).isFalse();
        assertThat(result.get().isResolutionBreached()).isTrue();
        verify(notificationService, times(1))
                .createSystem(any(), any(), eq(Notification.Type.ESCALATION), anyString(), anyString(), any(), any());
    }

    @Test
    void evaluate_calledAgainAfterAlreadyBreached_doesNotEscalateASecondTime() {
        UUID escalateToUserId = UUID.randomUUID();
        Ticket ticket = openTicket(Instant.now().minus(10, ChronoUnit.MINUTES));
        SlaPolicy policy = policy(1, 240, escalateToUserId);
        stubPolicyLookup(policy);

        ArgumentCaptor<TicketSlaStatus> captor = ArgumentCaptor.forClass(TicketSlaStatus.class);
        when(ticketSlaStatusRepository.findByTicketId(ticket.getId())).thenReturn(Optional.empty());
        when(ticketSlaStatusRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        slaEvaluationService.evaluate(ticket); // first call: breaches, escalates once
        TicketSlaStatus afterFirstCall = captor.getValue();

        when(ticketSlaStatusRepository.findByTicketId(ticket.getId())).thenReturn(Optional.of(afterFirstCall));

        slaEvaluationService.evaluate(ticket); // second call: still breached, must not escalate again

        verify(notificationService, times(1))
                .createSystem(any(), any(), eq(Notification.Type.ESCALATION), anyString(), anyString(), any(), any());
    }

    @Test
    void evaluate_breachedWithNoEscalationTarget_marksBreachedButNeverCallsNotificationService() {
        Ticket ticket = openTicket(Instant.now().minus(10, ChronoUnit.MINUTES));
        SlaPolicy policy = policy(1, 240, null); // no escalateToUserId configured
        stubPolicyLookup(policy);
        stubNoExistingStatus(ticket);

        Optional<TicketSlaStatus> result = slaEvaluationService.evaluate(ticket);

        assertThat(result).isPresent();
        assertThat(result.get().isResponseBreached()).isTrue();
        assertThat(result.get().isEscalated()).isFalse();
        verifyNoEscalation();
    }

    private void stubPolicyLookup(SlaPolicy policy) {
        when(slaPolicyRepository.findByOrganizationIdAndPriorityAndActiveTrue(organizationId, Ticket.Priority.HIGH))
                .thenReturn(Optional.of(policy));
    }

    private void stubNoExistingStatus(Ticket ticket) {
        when(ticketSlaStatusRepository.findByTicketId(ticket.getId())).thenReturn(Optional.empty());
        when(ticketSlaStatusRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void verifyNoEscalation() {
        verify(notificationService, never()).createSystem(any(), any(), any(), any(), any(), any(), any());
    }

    private Ticket openTicket(Instant createdAt) {
        Ticket ticket = new Ticket(organizationId, "Server is down", ownerId);
        ticket.setId(UUID.randomUUID());
        ticket.setPriority(Ticket.Priority.HIGH);
        ticket.setCreatedAt(createdAt);
        return ticket;
    }

    private SlaPolicy policy(int responseTargetMinutes, int resolutionTargetMinutes, UUID escalateToUserId) {
        SlaPolicy policy = new SlaPolicy(organizationId, "HIGH priority SLA", Ticket.Priority.HIGH, responseTargetMinutes, resolutionTargetMinutes);
        policy.setId(UUID.randomUUID());
        policy.setEscalateToUserId(escalateToUserId);
        return policy;
    }
}
