package com.aitrainercrm.platform.loyalty.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.loyalty.dto.CreateLoyaltyTransactionRequest;
import com.aitrainercrm.platform.loyalty.dto.UpdateLoyaltyTransactionRequest;
import com.aitrainercrm.platform.loyalty.entity.LoyaltyTransaction;
import com.aitrainercrm.platform.loyalty.repository.LoyaltyTransactionRepository;
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
 * A client's loyalty points ledger - see {@link LoyaltyTransaction}'s javadoc and V59's migration
 * comment for the backstory. Follows the same OWN/TEAM/DEPARTMENT/ORGANIZATION record-level
 * authorization shape as {@code ProgressPhotoService}, with {@code resolveOwner} defaulting a null
 * {@code ownerId} to the caller. {@link #assertSignMatchesReason} is the one piece of real business
 * logic on create/update; {@link #getBalance} is the one piece of real business logic on read - it
 * sums only the transactions visible to the caller under their granted scope, the exact same
 * {@code visibleOwnerIds} split {@link #list} uses, just aggregated instead of paged.
 */
@Service
@RequiredArgsConstructor
public class LoyaltyTransactionService {

    private static final Permission.Resource RESOURCE = Permission.Resource.LOYALTY_TRANSACTION;

    private final LoyaltyTransactionRepository loyaltyTransactionRepository;
    private final ContactRepository contactRepository;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<LoyaltyTransaction> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleOwnerIds
                .map(ownerIds -> loyaltyTransactionRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(principal.getOrganizationId(), ownerIds, pageable))
                .orElseGet(() -> loyaltyTransactionRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable));
    }

    @Transactional(readOnly = true)
    public LoyaltyTransaction get(UserPrincipal principal, UUID loyaltyTransactionId) {
        LoyaltyTransaction transaction = findOrThrow(principal.getOrganizationId(), loyaltyTransactionId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, transaction.getOwnerId());
        return transaction;
    }

    /**
     * Sums only the transactions the caller can see under their granted READ scope - an OWN-scoped
     * caller gets a partial balance (their own entries only), an ORGANIZATION-scoped caller gets the
     * contact's true full balance. Same restraint {@link #list} already applies to paged results,
     * just aggregated instead.
     */
    @Transactional(readOnly = true)
    public long getBalance(UserPrincipal principal, UUID contactId) {
        assertContactInOrganization(principal.getOrganizationId(), contactId);
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleOwnerIds
                .map(ownerIds -> loyaltyTransactionRepository.sumPointsByOrganizationIdAndContactIdAndOwnerIdIn(principal.getOrganizationId(), contactId, ownerIds))
                .orElseGet(() -> loyaltyTransactionRepository.sumPointsByOrganizationIdAndContactId(principal.getOrganizationId(), contactId));
    }

    @Transactional
    public LoyaltyTransaction create(UserPrincipal principal, CreateLoyaltyTransactionRequest request) {
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, request.ownerId());
        assertContactInOrganization(principal.getOrganizationId(), request.contactId());
        assertSignMatchesReason(request.points(), request.reason());

        LoyaltyTransaction transaction = new LoyaltyTransaction(principal.getOrganizationId(), request.contactId(), ownerId, request.points(), request.reason());
        transaction.setNotes(request.notes());
        loyaltyTransactionRepository.save(transaction);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "LoyaltyTransaction", transaction.getId()));
        return transaction;
    }

    @Transactional
    public LoyaltyTransaction update(UserPrincipal principal, UUID loyaltyTransactionId, UpdateLoyaltyTransactionRequest request) {
        LoyaltyTransaction transaction = findOrThrow(principal.getOrganizationId(), loyaltyTransactionId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, transaction.getOwnerId());
        assertSignMatchesReason(request.points(), request.reason());

        transaction.setPoints(request.points());
        transaction.setReason(request.reason());
        transaction.setNotes(request.notes());
        loyaltyTransactionRepository.save(transaction);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "LoyaltyTransaction", transaction.getId()));
        return transaction;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID loyaltyTransactionId) {
        LoyaltyTransaction transaction = findOrThrow(principal.getOrganizationId(), loyaltyTransactionId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, transaction.getOwnerId());

        transaction.setDeletedAt(Instant.now());
        loyaltyTransactionRepository.save(transaction);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "LoyaltyTransaction", loyaltyTransactionId));
    }

    /**
     * EARNED_CHECKIN/EARNED_REFERRAL must add points, REDEEMED_REWARD must spend them,
     * MANUAL_ADJUSTMENT can go either way - that's the point of it being the manual-override reason.
     */
    private void assertSignMatchesReason(int points, LoyaltyTransaction.Reason reason) {
        boolean valid = switch (reason) {
            case EARNED_CHECKIN, EARNED_REFERRAL -> points > 0;
            case REDEEMED_REWARD -> points < 0;
            case MANUAL_ADJUSTMENT -> points != 0;
        };
        if (!valid) {
            throw new BusinessException(
                    "LOYALTY_TRANSACTION_INVALID_SIGN",
                    "Points must be positive for earned reasons and negative for redemptions",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private LoyaltyTransaction findOrThrow(UUID organizationId, UUID loyaltyTransactionId) {
        return loyaltyTransactionRepository.findActiveByIdAndOrganizationId(loyaltyTransactionId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("LoyaltyTransaction", loyaltyTransactionId));
    }

    private UUID resolveOwner(UserPrincipal principal, Permission.Action action, UUID requestedOwnerId) {
        if (requestedOwnerId == null || requestedOwnerId.equals(principal.getId())) {
            return principal.getId();
        }
        if (scopeAuthorizationService.highestGranted(principal, RESOURCE, action) != ScopeAuthorizationService.Access.ORGANIZATION) {
            throw new ForbiddenException("You can only " + action.name().toLowerCase(Locale.ROOT) + " loyalty transactions you manage");
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
