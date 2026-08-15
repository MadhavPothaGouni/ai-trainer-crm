package com.aitrainercrm.platform.checkin.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.checkin.dto.CreateClientCheckInRequest;
import com.aitrainercrm.platform.checkin.dto.UpdateClientCheckInRequest;
import com.aitrainercrm.platform.checkin.entity.ClientCheckIn;
import com.aitrainercrm.platform.checkin.repository.ClientCheckInRepository;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
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
 * Facility check-ins - see {@link ClientCheckIn}'s javadoc and V52's migration comment for the
 * backstory. Follows the exact same shape as {@code TimeOffRequestService}: OWN/TEAM/DEPARTMENT/
 * ORGANIZATION record-level authorization via {@link ScopeAuthorizationService}, {@code
 * resolveOwner} defaulting a null {@code ownerId} to the caller, {@link #updateStatus} stamping
 * {@code checkedOutAt} the first time status moves to CHECKED_OUT.
 */
@Service
@RequiredArgsConstructor
public class ClientCheckInService {

    private static final Permission.Resource RESOURCE = Permission.Resource.CLIENT_CHECK_IN;

    private final ClientCheckInRepository clientCheckInRepository;
    private final ContactRepository contactRepository;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<ClientCheckIn> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleOwnerIds
                .map(ownerIds -> clientCheckInRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(principal.getOrganizationId(), ownerIds, pageable))
                .orElseGet(() -> clientCheckInRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable));
    }

    @Transactional(readOnly = true)
    public ClientCheckIn get(UserPrincipal principal, UUID clientCheckInId) {
        ClientCheckIn checkIn = findOrThrow(principal.getOrganizationId(), clientCheckInId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, checkIn.getOwnerId());
        return checkIn;
    }

    @Transactional
    public ClientCheckIn create(UserPrincipal principal, CreateClientCheckInRequest request) {
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, request.ownerId());
        if (!contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(request.contactId(), principal.getOrganizationId())) {
            throw new ResourceNotFoundException("Contact", request.contactId());
        }

        ClientCheckIn checkIn = new ClientCheckIn(principal.getOrganizationId(), request.contactId(), ownerId);
        checkIn.setMethod(request.method());
        checkIn.setNotes(request.notes());
        clientCheckInRepository.save(checkIn);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "ClientCheckIn", checkIn.getId()));
        return checkIn;
    }

    @Transactional
    public ClientCheckIn update(UserPrincipal principal, UUID clientCheckInId, UpdateClientCheckInRequest request) {
        ClientCheckIn checkIn = findOrThrow(principal.getOrganizationId(), clientCheckInId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, checkIn.getOwnerId());

        checkIn.setMethod(request.method());
        checkIn.setNotes(request.notes());
        clientCheckInRepository.save(checkIn);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "ClientCheckIn", checkIn.getId()));
        return checkIn;
    }

    /**
     * No invalid-transition checks, same restraint {@code TimeOffRequestService#updateStatus}'s
     * javadoc documents - correcting a mistaken CHECKED_OUT back to CHECKED_IN is a legitimate
     * correction. {@code checkedOutAt} is stamped the first time status moves to CHECKED_OUT and
     * never overwritten afterward.
     */
    @Transactional
    public ClientCheckIn updateStatus(UserPrincipal principal, UUID clientCheckInId, ClientCheckIn.Status newStatus) {
        ClientCheckIn checkIn = findOrThrow(principal.getOrganizationId(), clientCheckInId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, checkIn.getOwnerId());

        if (newStatus == ClientCheckIn.Status.CHECKED_OUT && checkIn.getCheckedOutAt() == null) {
            checkIn.setCheckedOutAt(Instant.now());
        }
        checkIn.setStatus(newStatus);
        clientCheckInRepository.save(checkIn);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "ClientCheckIn", checkIn.getId()));
        return checkIn;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID clientCheckInId) {
        ClientCheckIn checkIn = findOrThrow(principal.getOrganizationId(), clientCheckInId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, checkIn.getOwnerId());

        checkIn.setDeletedAt(Instant.now());
        clientCheckInRepository.save(checkIn);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "ClientCheckIn", clientCheckInId));
    }

    private ClientCheckIn findOrThrow(UUID organizationId, UUID clientCheckInId) {
        return clientCheckInRepository.findActiveByIdAndOrganizationId(clientCheckInId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("ClientCheckIn", clientCheckInId));
    }

    private UUID resolveOwner(UserPrincipal principal, Permission.Action action, UUID requestedOwnerId) {
        if (requestedOwnerId == null || requestedOwnerId.equals(principal.getId())) {
            return principal.getId();
        }
        if (scopeAuthorizationService.highestGranted(principal, RESOURCE, action) != ScopeAuthorizationService.Access.ORGANIZATION) {
            throw new ForbiddenException("You can only " + action.name().toLowerCase(Locale.ROOT) + " check-ins logged by yourself");
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
}
