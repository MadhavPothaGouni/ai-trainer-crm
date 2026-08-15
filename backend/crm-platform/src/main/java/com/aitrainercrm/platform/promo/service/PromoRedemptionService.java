package com.aitrainercrm.platform.promo.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.promo.dto.CreatePromoRedemptionRequest;
import com.aitrainercrm.platform.promo.dto.UpdatePromoRedemptionRequest;
import com.aitrainercrm.platform.promo.entity.PromoCode;
import com.aitrainercrm.platform.promo.entity.PromoRedemption;
import com.aitrainercrm.platform.promo.repository.PromoRedemptionRepository;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
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
 * One client's use of a {@link com.aitrainercrm.platform.promo.entity.PromoCode} - see
 * {@link PromoRedemption}'s javadoc and V51's migration comment for the backstory. Follows the
 * same OWN/TEAM/DEPARTMENT/ORGANIZATION record-level authorization shape as
 * {@code LockerAssignmentService}, with {@code resolveOwner} defaulting a null {@code ownerId} to
 * the caller, but with no {@link #updateStatus} counterpart - see {@link PromoRedemption}'s
 * javadoc for why a redemption has no status lifecycle. {@link #create} validates the code is
 * still usable (active, not expired, under its redemption cap) the same way
 * {@code ReferralService#issueReward} validates a reward is issuable before acting.
 */
@Service
@RequiredArgsConstructor
public class PromoRedemptionService {

    private static final Permission.Resource RESOURCE = Permission.Resource.PROMO_REDEMPTION;

    private final PromoRedemptionRepository promoRedemptionRepository;
    private final PromoCodeService promoCodeService;
    private final ContactRepository contactRepository;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<PromoRedemption> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleOwnerIds
                .map(ownerIds -> promoRedemptionRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(principal.getOrganizationId(), ownerIds, pageable))
                .orElseGet(() -> promoRedemptionRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable));
    }

    @Transactional(readOnly = true)
    public PromoRedemption get(UserPrincipal principal, UUID promoRedemptionId) {
        PromoRedemption redemption = findOrThrow(principal.getOrganizationId(), promoRedemptionId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, redemption.getOwnerId());
        return redemption;
    }

    @Transactional
    public PromoRedemption create(UserPrincipal principal, CreatePromoRedemptionRequest request) {
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, request.ownerId());
        PromoCode promoCode = promoCodeService.findOrThrow(principal.getOrganizationId(), request.promoCodeId());
        assertRedeemable(promoCode);
        if (!contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(request.contactId(), principal.getOrganizationId())) {
            throw new ResourceNotFoundException("Contact", request.contactId());
        }

        PromoRedemption redemption = new PromoRedemption(principal.getOrganizationId(), request.promoCodeId(), request.contactId(), ownerId);
        redemption.setOrderId(request.orderId());
        redemption.setAmountDiscounted(request.amountDiscounted());
        redemption.setNotes(request.notes());
        promoRedemptionRepository.save(redemption);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "PromoRedemption", redemption.getId()));
        return redemption;
    }

    @Transactional
    public PromoRedemption update(UserPrincipal principal, UUID promoRedemptionId, UpdatePromoRedemptionRequest request) {
        PromoRedemption redemption = findOrThrow(principal.getOrganizationId(), promoRedemptionId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, redemption.getOwnerId());

        redemption.setOrderId(request.orderId());
        redemption.setAmountDiscounted(request.amountDiscounted());
        redemption.setNotes(request.notes());
        promoRedemptionRepository.save(redemption);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "PromoRedemption", redemption.getId()));
        return redemption;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID promoRedemptionId) {
        PromoRedemption redemption = findOrThrow(principal.getOrganizationId(), promoRedemptionId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, redemption.getOwnerId());

        redemption.setDeletedAt(Instant.now());
        promoRedemptionRepository.save(redemption);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "PromoRedemption", promoRedemptionId));
    }

    private void assertRedeemable(PromoCode promoCode) {
        if (!promoCode.isActive()) {
            throw new BusinessException("PROMO_CODE_INACTIVE", "This promo code is not active", HttpStatus.CONFLICT);
        }
        if (promoCode.getExpiresAt() != null && promoCode.getExpiresAt().isBefore(LocalDate.now())) {
            throw new BusinessException("PROMO_CODE_EXPIRED", "This promo code has expired", HttpStatus.CONFLICT);
        }
        if (promoCode.getMaxRedemptions() != null) {
            long redeemed = promoRedemptionRepository.countByPromoCodeIdAndDeletedAtIsNull(promoCode.getId());
            if (redeemed >= promoCode.getMaxRedemptions()) {
                throw new BusinessException("PROMO_CODE_REDEMPTION_LIMIT_REACHED", "This promo code has reached its redemption limit", HttpStatus.CONFLICT);
            }
        }
    }

    private PromoRedemption findOrThrow(UUID organizationId, UUID promoRedemptionId) {
        return promoRedemptionRepository.findActiveByIdAndOrganizationId(promoRedemptionId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("PromoRedemption", promoRedemptionId));
    }

    private UUID resolveOwner(UserPrincipal principal, Permission.Action action, UUID requestedOwnerId) {
        if (requestedOwnerId == null || requestedOwnerId.equals(principal.getId())) {
            return principal.getId();
        }
        if (scopeAuthorizationService.highestGranted(principal, RESOURCE, action) != ScopeAuthorizationService.Access.ORGANIZATION) {
            throw new ForbiddenException("You can only " + action.name().toLowerCase(Locale.ROOT) + " redemptions applied by yourself");
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
