package com.aitrainercrm.platform.groupclass.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.groupclass.dto.CreateClassWaitlistRequest;
import com.aitrainercrm.platform.groupclass.dto.UpdateClassWaitlistRequest;
import com.aitrainercrm.platform.groupclass.entity.ClassWaitlist;
import com.aitrainercrm.platform.groupclass.repository.ClassWaitlistRepository;
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
 * A client queued for a spot in a full {@link ClassWaitlist} entry's parent {@code ClassSession} -
 * see {@link ClassWaitlist}'s javadoc and V61's migration comment for the backstory. Follows the
 * same OWN/TEAM/DEPARTMENT/ORGANIZATION record-level authorization shape as
 * {@code ClassAttendanceService}, with {@code resolveOwner} defaulting a null {@code ownerId} to
 * the caller. Injects {@link ClassSessionService} and calls its package-private
 * {@code findOrThrow} to validate a new entry's parent session - same package-co-location
 * precedent {@code RoomBookingService} established for {@code Room}.
 */
@Service
@RequiredArgsConstructor
public class ClassWaitlistService {

    private static final Permission.Resource RESOURCE = Permission.Resource.CLASS_WAITLIST;

    private final ClassWaitlistRepository classWaitlistRepository;
    private final ClassSessionService classSessionService;
    private final ContactRepository contactRepository;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<ClassWaitlist> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleOwnerIds
                .map(ownerIds -> classWaitlistRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(principal.getOrganizationId(), ownerIds, pageable))
                .orElseGet(() -> classWaitlistRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable));
    }

    @Transactional(readOnly = true)
    public ClassWaitlist get(UserPrincipal principal, UUID classWaitlistId) {
        ClassWaitlist waitlist = findOrThrow(principal.getOrganizationId(), classWaitlistId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, waitlist.getOwnerId());
        return waitlist;
    }

    /** {@code position} is computed here (count of WAITING entries for the session, plus one) - never accepted from the client. */
    @Transactional
    public ClassWaitlist create(UserPrincipal principal, CreateClassWaitlistRequest request) {
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, request.ownerId());
        classSessionService.findOrThrow(principal.getOrganizationId(), request.classSessionId());
        assertContactInOrganization(principal.getOrganizationId(), request.contactId());

        long waitingCount = classWaitlistRepository.countByClassSessionIdAndStatusAndDeletedAtIsNull(request.classSessionId(), ClassWaitlist.Status.WAITING);
        ClassWaitlist waitlist = new ClassWaitlist(
                principal.getOrganizationId(), request.classSessionId(), request.contactId(), ownerId, (int) waitingCount + 1);
        waitlist.setNotes(request.notes());
        classWaitlistRepository.save(waitlist);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "ClassWaitlist", waitlist.getId()));
        return waitlist;
    }

    @Transactional
    public ClassWaitlist update(UserPrincipal principal, UUID classWaitlistId, UpdateClassWaitlistRequest request) {
        ClassWaitlist waitlist = findOrThrow(principal.getOrganizationId(), classWaitlistId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, waitlist.getOwnerId());

        waitlist.setNotes(request.notes());
        classWaitlistRepository.save(waitlist);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "ClassWaitlist", waitlist.getId()));
        return waitlist;
    }

    /**
     * No invalid-transition checks - moving a CONVERTED entry back to WAITING is a legitimate
     * correction, same restraint every other status machine in this platform documents.
     * {@code notifiedAt} is stamped the first time status moves to NOTIFIED and never overwritten.
     */
    @Transactional
    public ClassWaitlist updateStatus(UserPrincipal principal, UUID classWaitlistId, ClassWaitlist.Status newStatus) {
        ClassWaitlist waitlist = findOrThrow(principal.getOrganizationId(), classWaitlistId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, waitlist.getOwnerId());

        if (newStatus == ClassWaitlist.Status.NOTIFIED && waitlist.getNotifiedAt() == null) {
            waitlist.setNotifiedAt(Instant.now());
        }
        waitlist.setStatus(newStatus);
        classWaitlistRepository.save(waitlist);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "ClassWaitlist", waitlist.getId()));
        return waitlist;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID classWaitlistId) {
        ClassWaitlist waitlist = findOrThrow(principal.getOrganizationId(), classWaitlistId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, waitlist.getOwnerId());

        waitlist.setDeletedAt(Instant.now());
        classWaitlistRepository.save(waitlist);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "ClassWaitlist", classWaitlistId));
    }

    private ClassWaitlist findOrThrow(UUID organizationId, UUID classWaitlistId) {
        return classWaitlistRepository.findActiveByIdAndOrganizationId(classWaitlistId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("ClassWaitlist", classWaitlistId));
    }

    private UUID resolveOwner(UserPrincipal principal, Permission.Action action, UUID requestedOwnerId) {
        if (requestedOwnerId == null || requestedOwnerId.equals(principal.getId())) {
            return principal.getId();
        }
        if (scopeAuthorizationService.highestGranted(principal, RESOURCE, action) != ScopeAuthorizationService.Access.ORGANIZATION) {
            throw new ForbiddenException("You can only " + action.name().toLowerCase(Locale.ROOT) + " waitlist entries you manage");
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

    private void assertContactInOrganization(UUID organizationId, UUID contactId) {
        if (!contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(contactId, organizationId)) {
            throw new ResourceNotFoundException("Contact", contactId);
        }
    }
}
