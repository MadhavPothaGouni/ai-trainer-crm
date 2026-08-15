package com.aitrainercrm.platform.membership.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.membership.dto.CreateMembershipRequest;
import com.aitrainercrm.platform.membership.dto.UpdateMembershipRequest;
import com.aitrainercrm.platform.membership.entity.Membership;
import com.aitrainercrm.platform.membership.entity.MembershipPlan;
import com.aitrainercrm.platform.membership.repository.MembershipRepository;
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
 * Memberships - see {@link Membership}'s javadoc and V42's migration comment for the backstory.
 * Follows the exact same shape as {@code ClientGoalService}/{@code ContractService}: OWN/TEAM/
 * DEPARTMENT/ORGANIZATION record-level authorization via {@link ScopeAuthorizationService},
 * {@code resolveOwner} defaulting a null {@code ownerId} to the caller. The one addition over
 * that shape: {@link #create} snapshots {@code billingCyclePrice}/{@code remainingCredits} off
 * the referenced {@link MembershipPlan} at creation time rather than accepting them directly on
 * the request - see the entity's javadoc for why a later plan price change must never
 * retroactively re-bill an existing member.
 */
@Service
@RequiredArgsConstructor
public class MembershipService {

    private static final Permission.Resource RESOURCE = Permission.Resource.MEMBERSHIP;

    private final MembershipRepository membershipRepository;
    private final MembershipPlanService membershipPlanService;
    private final ContactRepository contactRepository;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<Membership> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleOwnerIds
                .map(ownerIds -> membershipRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(principal.getOrganizationId(), ownerIds, pageable))
                .orElseGet(() -> membershipRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable));
    }

    @Transactional(readOnly = true)
    public Membership get(UserPrincipal principal, UUID membershipId) {
        Membership membership = findOrThrow(principal.getOrganizationId(), membershipId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, membership.getOwnerId());
        return membership;
    }

    @Transactional
    public Membership create(UserPrincipal principal, CreateMembershipRequest request) {
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, request.ownerId());
        assertContactInOrganization(principal.getOrganizationId(), request.contactId());
        MembershipPlan plan = membershipPlanService.findOrThrow(principal.getOrganizationId(), request.membershipPlanId());

        Membership membership = new Membership(principal.getOrganizationId(), request.contactId(), plan.getId(), ownerId, request.startDate());
        membership.setBillingCyclePrice(plan.getPrice());
        membership.setRemainingCredits(plan.getSessionCredits());
        membership.setNextBillingDate(request.nextBillingDate());
        membership.setAutoRenew(request.autoRenew());
        membership.setNotes(request.notes());
        membershipRepository.save(membership);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "Membership", membership.getId()));
        return membership;
    }

    @Transactional
    public Membership update(UserPrincipal principal, UUID membershipId, UpdateMembershipRequest request) {
        Membership membership = findOrThrow(principal.getOrganizationId(), membershipId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, membership.getOwnerId());

        membership.setEndDate(request.endDate());
        membership.setNextBillingDate(request.nextBillingDate());
        membership.setAutoRenew(request.autoRenew());
        membership.setRemainingCredits(request.remainingCredits());
        membership.setNotes(request.notes());
        membershipRepository.save(membership);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Membership", membership.getId()));
        return membership;
    }

    /**
     * No invalid-transition checks, same restraint {@code ClientGoalService#updateStatus}'s
     * javadoc documents - a client pausing, cancelling, and later resuming is completely normal.
     * {@code pausedAt}/{@code cancelledAt} are refreshed every time status moves INTO that state
     * (not stamped once) - see {@link Membership}'s javadoc for why that differs from
     * {@code ClientGoal#achievedAt}. Moving into CANCELLED or EXPIRED also clears
     * {@code nextBillingDate}, since there's nothing left to bill once either applies; moving
     * back to ACTIVE leaves it for the caller to set via {@link #update}, since only they know
     * the next real billing date after a reactivation.
     */
    @Transactional
    public Membership updateStatus(UserPrincipal principal, UUID membershipId, Membership.Status newStatus) {
        Membership membership = findOrThrow(principal.getOrganizationId(), membershipId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, membership.getOwnerId());

        if (newStatus == Membership.Status.PAUSED) {
            membership.setPausedAt(Instant.now());
        } else if (newStatus == Membership.Status.CANCELLED) {
            membership.setCancelledAt(Instant.now());
        }
        if (newStatus == Membership.Status.CANCELLED || newStatus == Membership.Status.EXPIRED) {
            membership.setNextBillingDate(null);
        }
        membership.setStatus(newStatus);
        membershipRepository.save(membership);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Membership", membership.getId()));
        return membership;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID membershipId) {
        Membership membership = findOrThrow(principal.getOrganizationId(), membershipId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, membership.getOwnerId());

        membership.setDeletedAt(Instant.now());
        membershipRepository.save(membership);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "Membership", membershipId));
    }

    /** Package-private (not private) so {@code MembershipFreezeService} can reuse it when validating a freeze's parent membership - same precedent {@code RoomService#findOrThrow} established for {@code RoomBookingService}. */
    Membership findOrThrow(UUID organizationId, UUID membershipId) {
        return membershipRepository.findActiveByIdAndOrganizationId(membershipId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership", membershipId));
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

    private void assertContactInOrganization(UUID organizationId, UUID contactId) {
        if (!contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(contactId, organizationId)) {
            throw new ResourceNotFoundException("Contact", contactId);
        }
    }
}
