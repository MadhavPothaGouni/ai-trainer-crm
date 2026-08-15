package com.aitrainercrm.platform.payment.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.payment.dto.CreateRefundRecordRequest;
import com.aitrainercrm.platform.payment.dto.UpdateRefundRecordRequest;
import com.aitrainercrm.platform.payment.entity.Payment;
import com.aitrainercrm.platform.payment.entity.RefundRecord;
import com.aitrainercrm.platform.payment.repository.RefundRecordRepository;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.math.BigDecimal;
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
 * A refund issued against a {@link Payment} - see {@link RefundRecord}'s javadoc and V65's
 * migration comment for the backstory. Follows the same OWN/TEAM/DEPARTMENT/ORGANIZATION
 * record-level authorization shape as {@code CompensationRecordService}, with
 * {@code resolveOwner} defaulting a null {@code ownerId} to the caller. Injects
 * {@link PaymentService} and calls its package-private {@code findOrThrow} to validate a new
 * refund's parent payment - same package-co-location precedent {@code RoomBookingService}
 * established for {@code Room}. {@link #assertRefundNotExceedingPayment} is the one piece of real
 * business logic: the sum of a payment's existing non-deleted refunds plus the new/edited amount
 * must never exceed the payment's own amount.
 */
@Service
@RequiredArgsConstructor
public class RefundRecordService {

    private static final Permission.Resource RESOURCE = Permission.Resource.REFUND_RECORD;

    private final RefundRecordRepository refundRecordRepository;
    private final PaymentService paymentService;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<RefundRecord> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleOwnerIds
                .map(ownerIds -> refundRecordRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(principal.getOrganizationId(), ownerIds, pageable))
                .orElseGet(() -> refundRecordRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable));
    }

    @Transactional(readOnly = true)
    public RefundRecord get(UserPrincipal principal, UUID refundRecordId) {
        RefundRecord refund = findOrThrow(principal.getOrganizationId(), refundRecordId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, refund.getOwnerId());
        return refund;
    }

    @Transactional
    public RefundRecord create(UserPrincipal principal, CreateRefundRecordRequest request) {
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, request.ownerId());
        Payment payment = paymentService.findOrThrow(principal.getOrganizationId(), request.paymentId());
        assertRefundNotExceedingPayment(payment, request.amount(), null);

        RefundRecord refund = new RefundRecord(principal.getOrganizationId(), request.paymentId(), ownerId, request.amount(), request.reason());
        refund.setNotes(request.notes());
        refundRecordRepository.save(refund);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "RefundRecord", refund.getId()));
        return refund;
    }

    @Transactional
    public RefundRecord update(UserPrincipal principal, UUID refundRecordId, UpdateRefundRecordRequest request) {
        RefundRecord refund = findOrThrow(principal.getOrganizationId(), refundRecordId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, refund.getOwnerId());

        Payment payment = paymentService.findOrThrow(principal.getOrganizationId(), refund.getPaymentId());
        assertRefundNotExceedingPayment(payment, request.amount(), refund.getId());

        refund.setAmount(request.amount());
        refund.setReason(request.reason());
        refund.setNotes(request.notes());
        refundRecordRepository.save(refund);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "RefundRecord", refund.getId()));
        return refund;
    }

    /**
     * No invalid-transition checks - moving a PROCESSED refund back to REQUESTED is a legitimate
     * correction, same restraint every other status machine in this platform documents.
     * {@code processedAt} is stamped once, the first time status moves to PROCESSED.
     */
    @Transactional
    public RefundRecord updateStatus(UserPrincipal principal, UUID refundRecordId, RefundRecord.Status newStatus) {
        RefundRecord refund = findOrThrow(principal.getOrganizationId(), refundRecordId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, refund.getOwnerId());

        if (newStatus == RefundRecord.Status.PROCESSED && refund.getProcessedAt() == null) {
            refund.setProcessedAt(Instant.now());
        }
        refund.setStatus(newStatus);
        refundRecordRepository.save(refund);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "RefundRecord", refund.getId()));
        return refund;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID refundRecordId) {
        RefundRecord refund = findOrThrow(principal.getOrganizationId(), refundRecordId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, refund.getOwnerId());

        refund.setDeletedAt(Instant.now());
        refundRecordRepository.save(refund);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "RefundRecord", refundRecordId));
    }

    /** A payment's refunds can never sum to more than the payment itself - see this class's javadoc. */
    private void assertRefundNotExceedingPayment(Payment payment, BigDecimal newAmount, UUID excludeRefundId) {
        BigDecimal alreadyRefunded = excludeRefundId == null
                ? refundRecordRepository.sumActiveAmountByPaymentId(payment.getId())
                : refundRecordRepository.sumActiveAmountByPaymentIdExcluding(payment.getId(), excludeRefundId);
        if (alreadyRefunded.add(newAmount).compareTo(payment.getAmount()) > 0) {
            throw new BusinessException(
                    "REFUND_RECORD_EXCEEDS_PAYMENT",
                    "Refunds for this payment can't total more than the payment's own amount of " + payment.getAmount(),
                    HttpStatus.CONFLICT);
        }
    }

    private RefundRecord findOrThrow(UUID organizationId, UUID refundRecordId) {
        return refundRecordRepository.findActiveByIdAndOrganizationId(refundRecordId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("RefundRecord", refundRecordId));
    }

    private UUID resolveOwner(UserPrincipal principal, Permission.Action action, UUID requestedOwnerId) {
        if (requestedOwnerId == null || requestedOwnerId.equals(principal.getId())) {
            return principal.getId();
        }
        if (scopeAuthorizationService.highestGranted(principal, RESOURCE, action) != ScopeAuthorizationService.Access.ORGANIZATION) {
            throw new ForbiddenException("You can only " + action.name().toLowerCase(Locale.ROOT) + " refunds you manage");
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
