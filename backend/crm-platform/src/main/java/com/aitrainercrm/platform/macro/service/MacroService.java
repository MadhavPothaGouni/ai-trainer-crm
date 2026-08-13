package com.aitrainercrm.platform.macro.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.macro.dto.CreateMacroRequest;
import com.aitrainercrm.platform.macro.dto.UpdateMacroRequest;
import com.aitrainercrm.platform.macro.entity.Macro;
import com.aitrainercrm.platform.macro.repository.MacroRepository;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.ticket.dto.UpdateTicketRequest;
import com.aitrainercrm.platform.ticket.entity.Ticket;
import com.aitrainercrm.platform.ticket.service.TicketService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The macro catalog, plus {@link #apply}. No {@link
 * com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService} call anywhere in
 * this class - no {@code ownerId} on {@link Macro}, same reasoning {@code CourseService}'s
 * javadoc gives, and {@link #apply}'s Ticket-side mutation is deliberately deferred entirely to
 * {@link TicketService}'s own already-correctly-authorized public methods (see V34's migration
 * comment for why: re-checking against MACRO's own permission instead would let anyone who can
 * merely *read* the macro catalog mutate a ticket they otherwise have no access to).
 */
@Service
@RequiredArgsConstructor
public class MacroService {

    private final MacroRepository macroRepository;
    private final TicketService ticketService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<Macro> list(UserPrincipal principal, Pageable pageable) {
        return macroRepository.findByOrganizationIdAndDeletedAtIsNullOrderByNameAsc(principal.getOrganizationId(), pageable);
    }

    @Transactional(readOnly = true)
    public List<Macro> listActive(UserPrincipal principal) {
        return macroRepository.findByOrganizationIdAndActiveTrueAndDeletedAtIsNullOrderByNameAsc(principal.getOrganizationId());
    }

    @Transactional(readOnly = true)
    public Macro get(UserPrincipal principal, UUID macroId) {
        return findOrThrow(principal.getOrganizationId(), macroId);
    }

    @Transactional
    public Macro create(UserPrincipal principal, CreateMacroRequest request) {
        Macro macro = new Macro(principal.getOrganizationId(), request.name(), request.body());
        macro.setNewStatus(request.newStatus());
        macroRepository.save(macro);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "Macro", macro.getId()));
        return macro;
    }

    @Transactional
    public Macro update(UserPrincipal principal, UUID macroId, UpdateMacroRequest request) {
        Macro macro = findOrThrow(principal.getOrganizationId(), macroId);
        macro.setName(request.name());
        macro.setBody(request.body());
        macro.setNewStatus(request.newStatus());
        macro.setActive(request.active());
        macroRepository.save(macro);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Macro", macro.getId()));
        return macro;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID macroId) {
        Macro macro = findOrThrow(principal.getOrganizationId(), macroId);
        macro.setDeletedAt(Instant.now());
        macroRepository.save(macro);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "Macro", macroId));
    }

    /**
     * Appends the macro's body to the ticket's description (via {@code TicketService#update},
     * reconstructing its other fields unchanged) and, if the macro carries one, moves the ticket
     * to {@link Macro#getNewStatus()} via {@code TicketService#updateStatus} - two calls into the
     * real, already-authorized Ticket API rather than one call into a shortcut. An inactive macro
     * can't be applied; the catalog stays browsable but not usable once retired, same as an
     * inactive Course/Sequence being excluded from {@code listActive} but still readable directly.
     */
    @Transactional
    public Ticket apply(UserPrincipal principal, UUID macroId, UUID ticketId) {
        Macro macro = findOrThrow(principal.getOrganizationId(), macroId);
        if (!macro.isActive()) {
            throw new BusinessException("MACRO_INACTIVE", "This macro has been deactivated and can no longer be applied", HttpStatus.CONFLICT);
        }

        Ticket ticket = ticketService.get(principal, ticketId);
        String appendedDescription = appendBody(ticket.getDescription(), macro.getBody());
        Ticket updated = ticketService.update(
                principal, ticketId, new UpdateTicketRequest(ticket.getSubject(), appendedDescription, ticket.getPriority(), ticket.getAccountId(), ticket.getContactId()));

        if (macro.getNewStatus() != null) {
            updated = ticketService.updateStatus(principal, ticketId, macro.getNewStatus());
        }
        return updated;
    }

    private String appendBody(String existingDescription, String macroBody) {
        if (existingDescription == null || existingDescription.isBlank()) {
            return macroBody;
        }
        String combined = existingDescription + "\n\n" + macroBody;
        // Ticket.description is capped at 2000 chars (V14) - truncate rather than let the update fail outright.
        return combined.length() > 2000 ? combined.substring(0, 2000) : combined;
    }

    private Macro findOrThrow(UUID organizationId, UUID macroId) {
        return macroRepository.findActiveByIdAndOrganizationId(macroId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Macro", macroId));
    }
}
