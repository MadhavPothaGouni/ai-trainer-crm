package com.aitrainercrm.platform.ticket.service;

import com.aitrainercrm.platform.account.repository.AccountRepository;
import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.ticket.dto.CreateTicketRequest;
import com.aitrainercrm.platform.ticket.dto.UpdateTicketRequest;
import com.aitrainercrm.platform.ticket.entity.Ticket;
import com.aitrainercrm.platform.ticket.repository.TicketRepository;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Support tickets - see {@link Ticket}'s javadoc and V14's migration comment for the backstory.
 * Follows the exact same shape as {@code AccountService}/{@code ContactService}: OWN/TEAM/
 * DEPARTMENT/ORGANIZATION record-level authorization via {@link ScopeAuthorizationService},
 * {@code resolveOwner} defaulting a null {@code ownerId} to the caller.
 */
@Service
@RequiredArgsConstructor
public class TicketService {

    private static final Permission.Resource RESOURCE = Permission.Resource.TICKET;

    private final TicketRepository ticketRepository;
    private final AccountRepository accountRepository;
    private final ContactRepository contactRepository;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<Ticket> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleOwnerIds
                .map(ownerIds -> ticketRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(principal.getOrganizationId(), ownerIds, pageable))
                .orElseGet(() -> ticketRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable));
    }

    @Transactional(readOnly = true)
    public Ticket get(UserPrincipal principal, UUID ticketId) {
        Ticket ticket = findOrThrow(principal.getOrganizationId(), ticketId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, ticket.getOwnerId());
        return ticket;
    }

    @Transactional
    public Ticket create(UserPrincipal principal, CreateTicketRequest request) {
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, request.ownerId());
        assertAccountInOrganization(principal.getOrganizationId(), request.accountId());
        assertContactInOrganization(principal.getOrganizationId(), request.contactId());

        Ticket ticket = new Ticket(principal.getOrganizationId(), request.subject(), ownerId);
        ticket.setDescription(request.description());
        ticket.setPriority(request.priority());
        ticket.setAccountId(request.accountId());
        ticket.setContactId(request.contactId());
        ticketRepository.save(ticket);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "Ticket", ticket.getId()));
        return ticket;
    }

    @Transactional
    public Ticket update(UserPrincipal principal, UUID ticketId, UpdateTicketRequest request) {
        Ticket ticket = findOrThrow(principal.getOrganizationId(), ticketId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, ticket.getOwnerId());
        assertAccountInOrganization(principal.getOrganizationId(), request.accountId());
        assertContactInOrganization(principal.getOrganizationId(), request.contactId());

        ticket.setSubject(request.subject());
        ticket.setDescription(request.description());
        ticket.setPriority(request.priority());
        ticket.setAccountId(request.accountId());
        ticket.setContactId(request.contactId());
        ticketRepository.save(ticket);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Ticket", ticket.getId()));
        return ticket;
    }

    /**
     * No invalid-transition checks, unlike {@code LeadService#updateStatus} or Order's DRAFT ->
     * CONFIRMED -> FULFILLED linearity - see V14's migration comment for why a support ticket's
     * status is intentionally not a one-way state machine. {@code resolvedAt} is stamped when moving
     * into RESOLVED/CLOSED and cleared when moving back out, so it always reflects "is this
     * currently in a resolved-ish state," not "was this ever resolved."
     */
    @Transactional
    public Ticket updateStatus(UserPrincipal principal, UUID ticketId, Ticket.Status newStatus) {
        Ticket ticket = findOrThrow(principal.getOrganizationId(), ticketId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, ticket.getOwnerId());

        ticket.setStatus(newStatus);
        ticket.setResolvedAt(isResolvedLike(newStatus) ? Instant.now() : null);
        ticketRepository.save(ticket);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Ticket", ticket.getId()));
        return ticket;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID ticketId) {
        Ticket ticket = findOrThrow(principal.getOrganizationId(), ticketId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, ticket.getOwnerId());

        ticket.setDeletedAt(Instant.now());
        ticketRepository.save(ticket);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "Ticket", ticket.getId()));
    }

    @Transactional
    public Ticket assignOwner(UserPrincipal principal, UUID ticketId, UUID newOwnerId) {
        Ticket ticket = findOrThrow(principal.getOrganizationId(), ticketId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.ASSIGN, ticket.getOwnerId());
        assertUserInOrganization(principal.getOrganizationId(), newOwnerId);

        ticket.setOwnerId(newOwnerId);
        ticketRepository.save(ticket);

        events.publishEvent(new CrmAuditEvents.RecordAssigned(principal.getId(), principal.getOrganizationId(), "Ticket", ticket.getId(), newOwnerId));
        return ticket;
    }

    private boolean isResolvedLike(Ticket.Status status) {
        return status == Ticket.Status.RESOLVED || status == Ticket.Status.CLOSED;
    }

    private Ticket findOrThrow(UUID organizationId, UUID ticketId) {
        return ticketRepository.findActiveByIdAndOrganizationId(ticketId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
    }

    private UUID resolveOwner(UserPrincipal principal, Permission.Action action, UUID requestedOwnerId) {
        if (requestedOwnerId == null || requestedOwnerId.equals(principal.getId())) {
            return principal.getId();
        }
        if (scopeAuthorizationService.highestGranted(principal, RESOURCE, action) != ScopeAuthorizationService.Access.ORGANIZATION) {
            throw new ForbiddenException("You can only " + action.name().toLowerCase(Locale.ROOT) + " records assigned to yourself");
        }
        assertUserInOrganization(principal.getOrganizationId(), requestedOwnerId);
        return requestedOwnerId;
    }

    private void assertUserInOrganization(UUID organizationId, UUID userId) {
        boolean exists = userRepository.findActiveById(userId).map(u -> organizationId.equals(u.getOrganizationId())).orElse(false);
        if (!exists) {
            throw new ResourceNotFoundException("User", userId);
        }
    }

    private void assertAccountInOrganization(UUID organizationId, UUID accountId) {
        if (accountId == null) return;
        if (!accountRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(accountId, organizationId)) {
            throw new ResourceNotFoundException("Account", accountId);
        }
    }

    private void assertContactInOrganization(UUID organizationId, UUID contactId) {
        if (contactId == null) return;
        if (!contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(contactId, organizationId)) {
            throw new ResourceNotFoundException("Contact", contactId);
        }
    }
}
