package com.aitrainercrm.platform.groupclass.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.groupclass.dto.CreateClassSessionRequest;
import com.aitrainercrm.platform.groupclass.dto.UpdateClassSessionRequest;
import com.aitrainercrm.platform.groupclass.entity.ClassSession;
import com.aitrainercrm.platform.groupclass.repository.ClassSessionRepository;
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
 * Scheduled occurrences of a {@link com.aitrainercrm.platform.groupclass.entity.GroupClass} - see
 * {@link ClassSession}'s javadoc for the owner-scoped shape. Follows the same shape as
 * {@code MembershipService}: {@code resolveOwner} defaulting a null {@code ownerId} to the
 * caller (the instructor running the session), a free {@code status} state machine with no
 * invalid-transition checks. {@link #findOrThrow} is package-private so
 * {@code ClassAttendanceService} can reuse it when validating a new attendance's parent session.
 */
@Service
@RequiredArgsConstructor
public class ClassSessionService {

    private static final Permission.Resource RESOURCE = Permission.Resource.CLASS_SESSION;

    private final ClassSessionRepository classSessionRepository;
    private final GroupClassService groupClassService;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<ClassSession> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleOwnerIds
                .map(ownerIds -> classSessionRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(principal.getOrganizationId(), ownerIds, pageable))
                .orElseGet(() -> classSessionRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable));
    }

    @Transactional(readOnly = true)
    public ClassSession get(UserPrincipal principal, UUID classSessionId) {
        ClassSession session = findOrThrow(principal.getOrganizationId(), classSessionId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, session.getOwnerId());
        return session;
    }

    @Transactional
    public ClassSession create(UserPrincipal principal, CreateClassSessionRequest request) {
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, request.ownerId());
        groupClassService.findOrThrow(principal.getOrganizationId(), request.groupClassId());

        ClassSession session = new ClassSession(principal.getOrganizationId(), request.groupClassId(), ownerId, request.startsAt(), request.endsAt());
        session.setCapacityOverride(request.capacityOverride());
        session.setNotes(request.notes());
        classSessionRepository.save(session);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "ClassSession", session.getId()));
        return session;
    }

    @Transactional
    public ClassSession update(UserPrincipal principal, UUID classSessionId, UpdateClassSessionRequest request) {
        ClassSession session = findOrThrow(principal.getOrganizationId(), classSessionId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, session.getOwnerId());

        session.setStartsAt(request.startsAt());
        session.setEndsAt(request.endsAt());
        session.setCapacityOverride(request.capacityOverride());
        session.setNotes(request.notes());
        classSessionRepository.save(session);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "ClassSession", session.getId()));
        return session;
    }

    /** No invalid-transition checks - a cancelled session being reinstated (the instructor recovered) is a normal correction, same restraint every other free status field in this platform shows. */
    @Transactional
    public ClassSession updateStatus(UserPrincipal principal, UUID classSessionId, ClassSession.Status newStatus) {
        ClassSession session = findOrThrow(principal.getOrganizationId(), classSessionId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, session.getOwnerId());

        session.setStatus(newStatus);
        classSessionRepository.save(session);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "ClassSession", session.getId()));
        return session;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID classSessionId) {
        ClassSession session = findOrThrow(principal.getOrganizationId(), classSessionId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, session.getOwnerId());

        session.setDeletedAt(Instant.now());
        classSessionRepository.save(session);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "ClassSession", classSessionId));
    }

    ClassSession findOrThrow(UUID organizationId, UUID classSessionId) {
        return classSessionRepository.findActiveByIdAndOrganizationId(classSessionId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("ClassSession", classSessionId));
    }

    private UUID resolveOwner(UserPrincipal principal, Permission.Action action, UUID requestedOwnerId) {
        if (requestedOwnerId == null || requestedOwnerId.equals(principal.getId())) {
            return principal.getId();
        }
        if (scopeAuthorizationService.highestGranted(principal, RESOURCE, action) != ScopeAuthorizationService.Access.ORGANIZATION) {
            throw new ForbiddenException("You can only " + action.name().toLowerCase(Locale.ROOT) + " sessions assigned to yourself");
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
