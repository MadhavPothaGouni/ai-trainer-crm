package com.aitrainercrm.platform.referral.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.referral.dto.CreateReferralRequest;
import com.aitrainercrm.platform.referral.dto.UpdateReferralRequest;
import com.aitrainercrm.platform.referral.entity.Referral;
import com.aitrainercrm.platform.referral.repository.ReferralRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Referrals - see {@link Referral}'s javadoc and V46's migration comment for the backstory.
 * Follows the exact same shape as {@code ClientGoalService}: OWN/TEAM/DEPARTMENT/ORGANIZATION
 * record-level authorization via {@link ScopeAuthorizationService}, {@code resolveOwner}
 * defaulting a null {@code ownerId} to the caller.
 */
@Service
@RequiredArgsConstructor
public class ReferralService {

    private static final Permission.Resource RESOURCE = Permission.Resource.REFERRAL;

    private final ReferralRepository referralRepository;
    private final ContactRepository contactRepository;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<Referral> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleOwnerIds
                .map(ownerIds -> referralRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(principal.getOrganizationId(), ownerIds, pageable))
                .orElseGet(() -> referralRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable));
    }

    @Transactional(readOnly = true)
    public Referral get(UserPrincipal principal, UUID referralId) {
        Referral referral = findOrThrow(principal.getOrganizationId(), referralId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, referral.getOwnerId());
        return referral;
    }

    @Transactional
    public Referral create(UserPrincipal principal, CreateReferralRequest request) {
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, request.ownerId());
        assertContactInOrganization(principal.getOrganizationId(), request.referrerContactId());

        Referral referral = new Referral(principal.getOrganizationId(), request.referrerContactId(), request.referredName(), ownerId);
        referral.setReferredEmail(request.referredEmail());
        referral.setReferredPhone(request.referredPhone());
        referral.setRewardAmount(request.rewardAmount());
        referral.setNotes(request.notes());
        referralRepository.save(referral);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "Referral", referral.getId()));
        return referral;
    }

    @Transactional
    public Referral update(UserPrincipal principal, UUID referralId, UpdateReferralRequest request) {
        Referral referral = findOrThrow(principal.getOrganizationId(), referralId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, referral.getOwnerId());

        referral.setReferredName(request.referredName());
        referral.setReferredEmail(request.referredEmail());
        referral.setReferredPhone(request.referredPhone());
        referral.setRewardAmount(request.rewardAmount());
        referral.setNotes(request.notes());
        referralRepository.save(referral);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Referral", referral.getId()));
        return referral;
    }

    /**
     * No invalid-transition checks, same restraint {@code ClientGoalService#updateStatus}'s
     * javadoc documents - moving a DECLINED referral back to CONTACTED is a legitimate
     * correction. {@code convertedContactId} is only applied when moving to CONVERTED and only
     * stamped the first time; it's ignored for every other status and ignored on a later
     * re-entry into CONVERTED so it never drifts once set.
     */
    @Transactional
    public Referral updateStatus(UserPrincipal principal, UUID referralId, Referral.Status newStatus, UUID convertedContactId) {
        Referral referral = findOrThrow(principal.getOrganizationId(), referralId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, referral.getOwnerId());

        if (newStatus == Referral.Status.CONVERTED && referral.getConvertedContactId() == null && convertedContactId != null) {
            assertContactInOrganization(principal.getOrganizationId(), convertedContactId);
            referral.setConvertedContactId(convertedContactId);
        }
        referral.setStatus(newStatus);
        referralRepository.save(referral);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Referral", referral.getId()));
        return referral;
    }

    /** Stamps rewardIssuedAt once - see {@link Referral}'s javadoc. Requires rewardAmount already be set; issuing a zero/unset reward is a data-entry mistake, not a valid action. */
    @Transactional
    public Referral issueReward(UserPrincipal principal, UUID referralId) {
        Referral referral = findOrThrow(principal.getOrganizationId(), referralId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, referral.getOwnerId());

        if (referral.getRewardAmount() == null) {
            throw new BusinessException("REFERRAL_REWARD_NOT_SET", "Set a reward amount before issuing it", HttpStatus.CONFLICT);
        }
        if (referral.getRewardIssuedAt() == null) {
            referral.setRewardIssuedAt(Instant.now());
        }
        referralRepository.save(referral);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Referral", referral.getId()));
        return referral;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID referralId) {
        Referral referral = findOrThrow(principal.getOrganizationId(), referralId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, referral.getOwnerId());

        referral.setDeletedAt(Instant.now());
        referralRepository.save(referral);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "Referral", referralId));
    }

    private Referral findOrThrow(UUID organizationId, UUID referralId) {
        return referralRepository.findActiveByIdAndOrganizationId(referralId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Referral", referralId));
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
