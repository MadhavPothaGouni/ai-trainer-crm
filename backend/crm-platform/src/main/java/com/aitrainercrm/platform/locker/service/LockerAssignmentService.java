package com.aitrainercrm.platform.locker.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.locker.dto.CreateLockerAssignmentRequest;
import com.aitrainercrm.platform.locker.dto.UpdateLockerAssignmentRequest;
import com.aitrainercrm.platform.locker.entity.LockerAssignment;
import com.aitrainercrm.platform.locker.repository.LockerAssignmentRepository;
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
 * One client's assignment to a {@link com.aitrainercrm.platform.locker.entity.Locker} - see
 * {@link LockerAssignment}'s javadoc and V50's migration comment for the backstory. Follows the
 * exact same shape as {@code PurchaseOrderService}: OWN/TEAM/DEPARTMENT/ORGANIZATION record-level
 * authorization via {@link ScopeAuthorizationService}, {@code resolveOwner} defaulting a null
 * {@code ownerId} to the caller, {@link #updateStatus} stamping {@code returnedAt} the first time
 * status moves to RETURNED.
 */
@Service
@RequiredArgsConstructor
public class LockerAssignmentService {

    private static final Permission.Resource RESOURCE = Permission.Resource.LOCKER_ASSIGNMENT;

    private final LockerAssignmentRepository lockerAssignmentRepository;
    private final LockerService lockerService;
    private final ContactRepository contactRepository;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<LockerAssignment> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleOwnerIds
                .map(ownerIds -> lockerAssignmentRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(principal.getOrganizationId(), ownerIds, pageable))
                .orElseGet(() -> lockerAssignmentRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable));
    }

    @Transactional(readOnly = true)
    public LockerAssignment get(UserPrincipal principal, UUID lockerAssignmentId) {
        LockerAssignment assignment = findOrThrow(principal.getOrganizationId(), lockerAssignmentId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, assignment.getOwnerId());
        return assignment;
    }

    @Transactional
    public LockerAssignment create(UserPrincipal principal, CreateLockerAssignmentRequest request) {
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, request.ownerId());
        lockerService.findOrThrow(principal.getOrganizationId(), request.lockerId());
        if (!contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(request.contactId(), principal.getOrganizationId())) {
            throw new ResourceNotFoundException("Contact", request.contactId());
        }

        LockerAssignment assignment = new LockerAssignment(principal.getOrganizationId(), request.lockerId(), request.contactId(), ownerId);
        assignment.setExpiresAt(request.expiresAt());
        assignment.setNotes(request.notes());
        lockerAssignmentRepository.save(assignment);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "LockerAssignment", assignment.getId()));
        return assignment;
    }

    @Transactional
    public LockerAssignment update(UserPrincipal principal, UUID lockerAssignmentId, UpdateLockerAssignmentRequest request) {
        LockerAssignment assignment = findOrThrow(principal.getOrganizationId(), lockerAssignmentId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, assignment.getOwnerId());

        assignment.setExpiresAt(request.expiresAt());
        assignment.setNotes(request.notes());
        lockerAssignmentRepository.save(assignment);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "LockerAssignment", assignment.getId()));
        return assignment;
    }

    /**
     * No invalid-transition checks, same restraint {@code PurchaseOrderService#updateStatus}'s
     * javadoc documents - reactivating an EXPIRED or RETURNED assignment is a legitimate
     * correction. {@code returnedAt} is stamped the first time status moves to RETURNED and never
     * overwritten afterward.
     */
    @Transactional
    public LockerAssignment updateStatus(UserPrincipal principal, UUID lockerAssignmentId, LockerAssignment.Status newStatus) {
        LockerAssignment assignment = findOrThrow(principal.getOrganizationId(), lockerAssignmentId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, assignment.getOwnerId());

        if (newStatus == LockerAssignment.Status.RETURNED && assignment.getReturnedAt() == null) {
            assignment.setReturnedAt(Instant.now());
        }
        assignment.setStatus(newStatus);
        lockerAssignmentRepository.save(assignment);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "LockerAssignment", assignment.getId()));
        return assignment;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID lockerAssignmentId) {
        LockerAssignment assignment = findOrThrow(principal.getOrganizationId(), lockerAssignmentId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, assignment.getOwnerId());

        assignment.setDeletedAt(Instant.now());
        lockerAssignmentRepository.save(assignment);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "LockerAssignment", lockerAssignmentId));
    }

    private LockerAssignment findOrThrow(UUID organizationId, UUID lockerAssignmentId) {
        return lockerAssignmentRepository.findActiveByIdAndOrganizationId(lockerAssignmentId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("LockerAssignment", lockerAssignmentId));
    }

    private UUID resolveOwner(UserPrincipal principal, Permission.Action action, UUID requestedOwnerId) {
        if (requestedOwnerId == null || requestedOwnerId.equals(principal.getId())) {
            return principal.getId();
        }
        if (scopeAuthorizationService.highestGranted(principal, RESOURCE, action) != ScopeAuthorizationService.Access.ORGANIZATION) {
            throw new ForbiddenException("You can only " + action.name().toLowerCase(Locale.ROOT) + " assignments made by yourself");
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
