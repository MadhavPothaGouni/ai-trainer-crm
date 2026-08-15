package com.aitrainercrm.platform.giftcard.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.giftcard.dto.CreateGiftCardRequest;
import com.aitrainercrm.platform.giftcard.dto.UpdateGiftCardRequest;
import com.aitrainercrm.platform.giftcard.entity.GiftCard;
import com.aitrainercrm.platform.giftcard.repository.GiftCardRepository;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.math.BigDecimal;
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
 * A prepaid balance issued to a client - see {@link GiftCard}'s javadoc and V54's migration
 * comment for the backstory. Follows the same owner-scoped shape as
 * {@code LockerAssignmentService}: OWN/TEAM/DEPARTMENT/ORGANIZATION record-level authorization via
 * {@link ScopeAuthorizationService}, {@code resolveOwner} defaulting a null {@code ownerId} to the
 * caller. What's new here is {@link #redeem}, a business-rule-checked balance deduction (mirroring
 * {@code PromoRedemptionService#assertRedeemable}'s typed-{@link BusinessException} pattern)
 * rather than a plain status flip - see this class's method javadoc for the specific checks.
 */
@Service
@RequiredArgsConstructor
public class GiftCardService {

    private static final Permission.Resource RESOURCE = Permission.Resource.GIFT_CARD;

    private final GiftCardRepository giftCardRepository;
    private final ContactRepository contactRepository;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<GiftCard> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleOwnerIds
                .map(ownerIds -> giftCardRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(principal.getOrganizationId(), ownerIds, pageable))
                .orElseGet(() -> giftCardRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable));
    }

    @Transactional(readOnly = true)
    public GiftCard get(UserPrincipal principal, UUID giftCardId) {
        GiftCard giftCard = findOrThrow(principal.getOrganizationId(), giftCardId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, giftCard.getOwnerId());
        return giftCard;
    }

    @Transactional
    public GiftCard create(UserPrincipal principal, CreateGiftCardRequest request) {
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, request.ownerId());
        if (!contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(request.contactId(), principal.getOrganizationId())) {
            throw new ResourceNotFoundException("Contact", request.contactId());
        }

        GiftCard giftCard = new GiftCard(
                principal.getOrganizationId(), request.contactId(), ownerId, request.code().trim().toUpperCase(), request.initialBalance());
        giftCard.setExpiresAt(request.expiresAt());
        giftCard.setNotes(request.notes());
        giftCardRepository.save(giftCard);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "GiftCard", giftCard.getId()));
        return giftCard;
    }

    @Transactional
    public GiftCard update(UserPrincipal principal, UUID giftCardId, UpdateGiftCardRequest request) {
        GiftCard giftCard = findOrThrow(principal.getOrganizationId(), giftCardId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, giftCard.getOwnerId());

        giftCard.setExpiresAt(request.expiresAt());
        giftCard.setNotes(request.notes());
        giftCardRepository.save(giftCard);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "GiftCard", giftCard.getId()));
        return giftCard;
    }

    /**
     * No invalid-transition checks - reactivating a cancelled or expired card is a legitimate
     * correction, same restraint every other status machine in this platform documents. Doesn't
     * touch {@code currentBalance} either way - only {@link #redeem} does that.
     */
    @Transactional
    public GiftCard updateStatus(UserPrincipal principal, UUID giftCardId, GiftCard.Status newStatus) {
        GiftCard giftCard = findOrThrow(principal.getOrganizationId(), giftCardId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, giftCard.getOwnerId());

        giftCard.setStatus(newStatus);
        giftCardRepository.save(giftCard);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "GiftCard", giftCard.getId()));
        return giftCard;
    }

    /**
     * Deducts {@code amount} from {@code currentBalance}, after checking: the card is ACTIVE
     * (else {@code GIFT_CARD_NOT_ACTIVE}), it isn't past its {@code expiresAt} (else
     * {@code GIFT_CARD_EXPIRED}), and {@code amount} doesn't exceed the remaining balance (else
     * {@code GIFT_CARD_INSUFFICIENT_BALANCE}) - same typed-{@link BusinessException} pattern
     * {@code PromoRedemptionService#assertRedeemable} established. If the deduction brings
     * {@code currentBalance} to exactly zero, status moves to REDEEMED and {@code redeemedAt} is
     * stamped - but only the first time; a card that's partially redeemed multiple times down to
     * zero doesn't re-stamp on a later, unrelated correction.
     */
    @Transactional
    public GiftCard redeem(UserPrincipal principal, UUID giftCardId, BigDecimal amount) {
        GiftCard giftCard = findOrThrow(principal.getOrganizationId(), giftCardId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, giftCard.getOwnerId());

        if (giftCard.getStatus() != GiftCard.Status.ACTIVE) {
            throw new BusinessException("GIFT_CARD_NOT_ACTIVE", "This gift card is not active", HttpStatus.CONFLICT);
        }
        if (giftCard.getExpiresAt() != null && giftCard.getExpiresAt().isBefore(LocalDate.now())) {
            throw new BusinessException("GIFT_CARD_EXPIRED", "This gift card has expired", HttpStatus.CONFLICT);
        }
        if (amount.compareTo(giftCard.getCurrentBalance()) > 0) {
            throw new BusinessException("GIFT_CARD_INSUFFICIENT_BALANCE", "This gift card's remaining balance is less than the requested amount", HttpStatus.CONFLICT);
        }

        giftCard.setCurrentBalance(giftCard.getCurrentBalance().subtract(amount));
        if (giftCard.getCurrentBalance().compareTo(BigDecimal.ZERO) == 0) {
            giftCard.setStatus(GiftCard.Status.REDEEMED);
            if (giftCard.getRedeemedAt() == null) {
                giftCard.setRedeemedAt(Instant.now());
            }
        }
        giftCardRepository.save(giftCard);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "GiftCard", giftCard.getId()));
        return giftCard;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID giftCardId) {
        GiftCard giftCard = findOrThrow(principal.getOrganizationId(), giftCardId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, giftCard.getOwnerId());

        giftCard.setDeletedAt(Instant.now());
        giftCardRepository.save(giftCard);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "GiftCard", giftCardId));
    }

    private GiftCard findOrThrow(UUID organizationId, UUID giftCardId) {
        return giftCardRepository.findActiveByIdAndOrganizationId(giftCardId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("GiftCard", giftCardId));
    }

    private UUID resolveOwner(UserPrincipal principal, Permission.Action action, UUID requestedOwnerId) {
        if (requestedOwnerId == null || requestedOwnerId.equals(principal.getId())) {
            return principal.getId();
        }
        if (scopeAuthorizationService.highestGranted(principal, RESOURCE, action) != ScopeAuthorizationService.Access.ORGANIZATION) {
            throw new ForbiddenException("You can only " + action.name().toLowerCase(Locale.ROOT) + " gift cards issued by yourself");
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
