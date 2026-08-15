package com.aitrainercrm.platform.membership.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.membership.dto.CreateMembershipFreezeRequest;
import com.aitrainercrm.platform.membership.dto.UpdateMembershipFreezeRequest;
import com.aitrainercrm.platform.membership.entity.MembershipFreeze;
import com.aitrainercrm.platform.membership.repository.MembershipFreezeRepository;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A client pausing an active {@code Membership} for a date range - see {@link MembershipFreeze}'s
 * javadoc and V62's migration comment for the backstory. Follows the same shape as
 * {@code RoomBookingService}: OWN/TEAM/DEPARTMENT/ORGANIZATION record-level authorization,
 * {@code resolveOwner} defaulting a null {@code ownerId} to the caller, and the same
 * overlap-checked-create/update/re-activate pattern {@code RoomBookingService#assertNoOverlap}
 * established - adapted here for {@link LocalDate} ranges instead of {@link java.time.Instant}
 * ranges, and REQUESTED/ACTIVE (instead of just CONFIRMED) as the "counts toward a conflict" set.
 */
@Service
@RequiredArgsConstructor
public class MembershipFreezeService {

    private static final Permission.Resource RESOURCE = Permission.Resource.MEMBERSHIP_FREEZE;
    private static final Set<MembershipFreeze.Status> CONFLICTING_STATUSES =
            EnumSet.of(MembershipFreeze.Status.REQUESTED, MembershipFreeze.Status.ACTIVE);

    private final MembershipFreezeRepository membershipFreezeRepository;
    private final MembershipService membershipService;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<MembershipFreeze> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleOwnerIds
                .map(ownerIds ->
                        membershipFreezeRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(principal.getOrganizationId(), ownerIds, pageable))
                .orElseGet(() -> membershipFreezeRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable));
    }

    @Transactional(readOnly = true)
    public MembershipFreeze get(UserPrincipal principal, UUID membershipFreezeId) {
        MembershipFreeze freeze = findOrThrow(principal.getOrganizationId(), membershipFreezeId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, freeze.getOwnerId());
        return freeze;
    }

    @Transactional
    public MembershipFreeze create(UserPrincipal principal, CreateMembershipFreezeRequest request) {
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, request.ownerId());
        membershipService.findOrThrow(principal.getOrganizationId(), request.membershipId());
        assertValidRange(request.freezeStart(), request.freezeEnd());
        assertNoOverlap(request.membershipId(), request.freezeStart(), request.freezeEnd(), null);

        MembershipFreeze freeze =
                new MembershipFreeze(principal.getOrganizationId(), request.membershipId(), ownerId, request.freezeStart(), request.freezeEnd());
        freeze.setReason(request.reason());
        freeze.setNotes(request.notes());
        membershipFreezeRepository.save(freeze);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "MembershipFreeze", freeze.getId()));
        return freeze;
    }

    @Transactional
    public MembershipFreeze update(UserPrincipal principal, UUID membershipFreezeId, UpdateMembershipFreezeRequest request) {
        MembershipFreeze freeze = findOrThrow(principal.getOrganizationId(), membershipFreezeId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, freeze.getOwnerId());

        assertValidRange(request.freezeStart(), request.freezeEnd());
        if (CONFLICTING_STATUSES.contains(freeze.getStatus())) {
            assertNoOverlap(freeze.getMembershipId(), request.freezeStart(), request.freezeEnd(), freeze.getId());
        }

        freeze.setFreezeStart(request.freezeStart());
        freeze.setFreezeEnd(request.freezeEnd());
        freeze.setReason(request.reason());
        freeze.setNotes(request.notes());
        membershipFreezeRepository.save(freeze);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "MembershipFreeze", freeze.getId()));
        return freeze;
    }

    /**
     * No invalid-transition checks - moving an ENDED freeze back to ACTIVE is a legitimate
     * correction, same restraint every other status machine in this platform documents. Moving
     * *to* ACTIVE re-checks {@link #assertNoOverlap}, since the membership's freeze schedule may
     * have filled in while this freeze sat ENDED; moving *away* from ACTIVE never needs the check.
     */
    @Transactional
    public MembershipFreeze updateStatus(UserPrincipal principal, UUID membershipFreezeId, MembershipFreeze.Status newStatus) {
        MembershipFreeze freeze = findOrThrow(principal.getOrganizationId(), membershipFreezeId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, freeze.getOwnerId());

        if (newStatus == MembershipFreeze.Status.ACTIVE && freeze.getStatus() != MembershipFreeze.Status.ACTIVE) {
            assertNoOverlap(freeze.getMembershipId(), freeze.getFreezeStart(), freeze.getFreezeEnd(), freeze.getId());
        }
        freeze.setStatus(newStatus);
        membershipFreezeRepository.save(freeze);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "MembershipFreeze", freeze.getId()));
        return freeze;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID membershipFreezeId) {
        MembershipFreeze freeze = findOrThrow(principal.getOrganizationId(), membershipFreezeId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, freeze.getOwnerId());

        freeze.setDeletedAt(Instant.now());
        membershipFreezeRepository.save(freeze);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "MembershipFreeze", membershipFreezeId));
    }

    private void assertValidRange(LocalDate freezeStart, LocalDate freezeEnd) {
        if (!freezeEnd.isAfter(freezeStart)) {
            throw new BusinessException(
                    "MEMBERSHIP_FREEZE_INVALID_RANGE", "The freeze's end date must be after its start date", HttpStatus.BAD_REQUEST);
        }
    }

    /** A membership can't hold two REQUESTED/ACTIVE freezes with overlapping [freezeStart, freezeEnd) date ranges - see this class's javadoc. */
    private void assertNoOverlap(UUID membershipId, LocalDate freezeStart, LocalDate freezeEnd, UUID excludeFreezeId) {
        boolean overlaps = excludeFreezeId == null
                ? membershipFreezeRepository.existsByMembershipIdAndStatusInAndDeletedAtIsNullAndFreezeStartLessThanAndFreezeEndGreaterThan(
                        membershipId, CONFLICTING_STATUSES, freezeEnd, freezeStart)
                : membershipFreezeRepository.existsByMembershipIdAndStatusInAndDeletedAtIsNullAndIdNotAndFreezeStartLessThanAndFreezeEndGreaterThan(
                        membershipId, CONFLICTING_STATUSES, excludeFreezeId, freezeEnd, freezeStart);
        if (overlaps) {
            throw new BusinessException(
                    "MEMBERSHIP_FREEZE_CONFLICT", "This membership already has a freeze for an overlapping date range", HttpStatus.CONFLICT);
        }
    }

    private MembershipFreeze findOrThrow(UUID organizationId, UUID membershipFreezeId) {
        return membershipFreezeRepository.findActiveByIdAndOrganizationId(membershipFreezeId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("MembershipFreeze", membershipFreezeId));
    }

    private UUID resolveOwner(UserPrincipal principal, Permission.Action action, UUID requestedOwnerId) {
        if (requestedOwnerId == null || requestedOwnerId.equals(principal.getId())) {
            return principal.getId();
        }
        if (scopeAuthorizationService.highestGranted(principal, RESOURCE, action) != ScopeAuthorizationService.Access.ORGANIZATION) {
            throw new ForbiddenException("You can only " + action.name().toLowerCase(Locale.ROOT) + " freezes you manage");
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
