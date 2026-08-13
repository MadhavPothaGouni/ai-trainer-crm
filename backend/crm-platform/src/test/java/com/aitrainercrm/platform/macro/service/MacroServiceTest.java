package com.aitrainercrm.platform.macro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.macro.dto.CreateMacroRequest;
import com.aitrainercrm.platform.macro.entity.Macro;
import com.aitrainercrm.platform.macro.repository.MacroRepository;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.ticket.dto.UpdateTicketRequest;
import com.aitrainercrm.platform.ticket.entity.Ticket;
import com.aitrainercrm.platform.ticket.service.TicketService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * See {@link MacroService}'s javadoc for why {@link TicketService} is mocked wholesale here
 * rather than {@code TicketRepository} - {@link MacroService#apply} only ever calls
 * TicketService's own public, already-authorized methods, the same "mock the collaborator
 * service, not its repository" precedent {@code CourseEnrollmentServiceTest} established for
 * {@code CourseService}.
 */
@ExtendWith(MockitoExtension.class)
class MacroServiceTest {

    @Mock private MacroRepository macroRepository;
    @Mock private TicketService ticketService;
    @Mock private ApplicationEventPublisher events;

    private MacroService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new MacroService(macroRepository, ticketService, events);
    }

    private UserPrincipal principal() {
        return new UserPrincipal(callerId, "agent@example.com", organizationId, List.of());
    }

    @Test
    void create_savesWithOptionalNewStatus() {
        Macro result = service.create(principal(), new CreateMacroRequest("Closing note", "Thanks for reaching out!", Ticket.Status.RESOLVED));

        assertThat(result.getName()).isEqualTo("Closing note");
        assertThat(result.getNewStatus()).isEqualTo(Ticket.Status.RESOLVED);
        org.mockito.Mockito.verify(macroRepository).save(result);
    }

    @Test
    void apply_appendsBodyToExistingDescriptionAndTransitionsStatus() {
        UUID macroId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        Macro macro = macro(macroId, "Thanks for reaching out!", Ticket.Status.RESOLVED);
        Ticket ticket = ticket(ticketId, "Can't log in", Ticket.Priority.HIGH);
        when(macroRepository.findActiveByIdAndOrganizationId(macroId, organizationId)).thenReturn(Optional.of(macro));
        when(ticketService.get(any(), eq(ticketId))).thenReturn(ticket);
        Ticket afterDescriptionUpdate = ticket(ticketId, "Can't log in\n\nThanks for reaching out!", Ticket.Priority.HIGH);
        when(ticketService.update(any(), eq(ticketId), any(UpdateTicketRequest.class))).thenReturn(afterDescriptionUpdate);
        Ticket afterStatusUpdate = ticket(ticketId, "Can't log in\n\nThanks for reaching out!", Ticket.Priority.HIGH);
        afterStatusUpdate.setStatus(Ticket.Status.RESOLVED);
        when(ticketService.updateStatus(any(), eq(ticketId), eq(Ticket.Status.RESOLVED))).thenReturn(afterStatusUpdate);

        Ticket result = service.apply(principal(), macroId, ticketId);

        verify(ticketService).update(any(), eq(ticketId), org.mockito.ArgumentMatchers.argThat(
                (UpdateTicketRequest req) -> req.description().equals("Can't log in\n\nThanks for reaching out!")));
        verify(ticketService).updateStatus(any(), eq(ticketId), eq(Ticket.Status.RESOLVED));
        assertThat(result.getStatus()).isEqualTo(Ticket.Status.RESOLVED);
    }

    @Test
    void apply_macroWithNoNewStatus_onlyUpdatesDescription() {
        UUID macroId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        Macro macro = macro(macroId, "Following up as promised.", null);
        Ticket ticket = ticket(ticketId, null, Ticket.Priority.MEDIUM);
        when(macroRepository.findActiveByIdAndOrganizationId(macroId, organizationId)).thenReturn(Optional.of(macro));
        when(ticketService.get(any(), eq(ticketId))).thenReturn(ticket);
        when(ticketService.update(any(), eq(ticketId), any(UpdateTicketRequest.class))).thenReturn(ticket);

        service.apply(principal(), macroId, ticketId);

        verify(ticketService, never()).updateStatus(any(), any(), any());
    }

    @Test
    void apply_inactiveMacro_isRejected() {
        UUID macroId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        Macro macro = macro(macroId, "Old macro", null);
        macro.setActive(false);
        when(macroRepository.findActiveByIdAndOrganizationId(macroId, organizationId)).thenReturn(Optional.of(macro));

        assertThatThrownBy(() -> service.apply(principal(), macroId, ticketId)).isInstanceOf(BusinessException.class);
        verify(ticketService, never()).get(any(), any());
    }

    @Test
    void apply_veryLongCombinedDescription_isTruncatedNotRejected() {
        UUID macroId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        Macro macro = macro(macroId, "x".repeat(1900), null);
        Ticket ticket = ticket(ticketId, "y".repeat(1900), Ticket.Priority.LOW);
        when(macroRepository.findActiveByIdAndOrganizationId(macroId, organizationId)).thenReturn(Optional.of(macro));
        when(ticketService.get(any(), eq(ticketId))).thenReturn(ticket);
        when(ticketService.update(any(), eq(ticketId), any(UpdateTicketRequest.class))).thenReturn(ticket);

        service.apply(principal(), macroId, ticketId);

        verify(ticketService).update(any(), eq(ticketId), org.mockito.ArgumentMatchers.argThat(
                (UpdateTicketRequest req) -> req.description().length() == 2000));
    }

    private Macro macro(UUID id, String body, Ticket.Status newStatus) {
        Macro macro = new Macro(organizationId, "Macro", body);
        macro.setId(id);
        macro.setNewStatus(newStatus);
        return macro;
    }

    private Ticket ticket(UUID id, String description, Ticket.Priority priority) {
        Ticket ticket = new Ticket(organizationId, "Subject", callerId);
        ticket.setId(id);
        ticket.setDescription(description);
        ticket.setPriority(priority);
        return ticket;
    }
}
