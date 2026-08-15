package com.aitrainercrm.platform.shift.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.shift.dto.CreateShiftRequest;
import com.aitrainercrm.platform.shift.dto.UpdateShiftRequest;
import com.aitrainercrm.platform.shift.entity.Shift;
import com.aitrainercrm.platform.shift.repository.ShiftRepository;
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
 * One employee's actual shift - see {@link Shift}'s javadoc for the owner-scoped shape. Follows
 * the same pattern as {@code MembershipService}/{@code ClassSessionService}: {@code resolveOwner}
 * defaulting a null {@code ownerId} to the caller, free status transitions. The one addition:
 * {@link #updateStatus} stamps {@code clockInAt}/{@code clockOutAt} the first time status moves
 * to IN_PROGRESS/COMPLETED respectively - never overwritten by a later correction (see
 * {@link Shift}'s javadoc).
 */
@Service
@RequiredArgsConstructor
public class ShiftService {

    private static final Permission.Resource RESOURCE = Permission.Resource.SHIFT;

    private final ShiftRepository shiftRepository;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<Shift> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleOwnerIds
                .map(ownerIds -> shiftRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(principal.getOrganizationId(), ownerIds, pageable))
                .orElseGet(() -> shiftRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable));
    }

    @Transactional(readOnly = true)
    public Shift get(UserPrincipal principal, UUID shiftId) {
        Shift shift = findOrThrow(principal.getOrganizationId(), shiftId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, shift.getOwnerId());
        return shift;
    }

    @Transactional
    public Shift create(UserPrincipal principal, CreateShiftRequest request) {
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, request.ownerId());

        Shift shift = new Shift(principal.getOrganizationId(), ownerId, request.shiftDate(), request.startsAt(), request.endsAt());
        shift.setShiftTemplateId(request.shiftTemplateId());
        shift.setNotes(request.notes());
        shiftRepository.save(shift);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "Shift", shift.getId()));
        return shift;
    }

    @Transactional
    public Shift update(UserPrincipal principal, UUID shiftId, UpdateShiftRequest request) {
        Shift shift = findOrThrow(principal.getOrganizationId(), shiftId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, shift.getOwnerId());

        shift.setShiftDate(request.shiftDate());
        shift.setStartsAt(request.startsAt());
        shift.setEndsAt(request.endsAt());
        shift.setNotes(request.notes());
        shiftRepository.save(shift);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Shift", shift.getId()));
        return shift;
    }

    @Transactional
    public Shift updateStatus(UserPrincipal principal, UUID shiftId, Shift.Status newStatus) {
        Shift shift = findOrThrow(principal.getOrganizationId(), shiftId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, shift.getOwnerId());

        if (newStatus == Shift.Status.IN_PROGRESS && shift.getClockInAt() == null) {
            shift.setClockInAt(Instant.now());
        }
        if (newStatus == Shift.Status.COMPLETED && shift.getClockOutAt() == null) {
            shift.setClockOutAt(Instant.now());
        }
        shift.setStatus(newStatus);
        shiftRepository.save(shift);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Shift", shift.getId()));
        return shift;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID shiftId) {
        Shift shift = findOrThrow(principal.getOrganizationId(), shiftId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, shift.getOwnerId());

        shift.setDeletedAt(Instant.now());
        shiftRepository.save(shift);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "Shift", shiftId));
    }

    private Shift findOrThrow(UUID organizationId, UUID shiftId) {
        return shiftRepository.findActiveByIdAndOrganizationId(shiftId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift", shiftId));
    }

    private UUID resolveOwner(UserPrincipal principal, Permission.Action action, UUID requestedOwnerId) {
        if (requestedOwnerId == null || requestedOwnerId.equals(principal.getId())) {
            return principal.getId();
        }
        if (scopeAuthorizationService.highestGranted(principal, RESOURCE, action) != ScopeAuthorizationService.Access.ORGANIZATION) {
            throw new ForbiddenException("You can only " + action.name().toLowerCase(Locale.ROOT) + " shifts assigned to yourself");
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
