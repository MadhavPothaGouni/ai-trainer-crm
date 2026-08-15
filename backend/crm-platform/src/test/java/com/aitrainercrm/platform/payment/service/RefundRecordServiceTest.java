package com.aitrainercrm.platform.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.payment.dto.CreateRefundRecordRequest;
import com.aitrainercrm.platform.payment.dto.UpdateRefundRecordRequest;
import com.aitrainercrm.platform.payment.entity.Payment;
import com.aitrainercrm.platform.payment.entity.RefundRecord;
import com.aitrainercrm.platform.payment.repository.RefundRecordRepository;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** See {@link RefundRecordService}'s javadoc for the "refunds can't exceed the payment" rule this mostly exists to cover. */
@ExtendWith(MockitoExtension.class)
class RefundRecordServiceTest {

    @Mock private RefundRecordRepository refundRecordRepository;
    @Mock private PaymentService paymentService;
    @Mock private UserRepository userRepository;
    @Mock private ScopeAuthorizationService scopeAuthorizationService;
    @Mock private ApplicationEventPublisher events;

    private RefundRecordService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID paymentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RefundRecordService(refundRecordRepository, paymentService, userRepository, scopeAuthorizationService, events);
    }

    private UserPrincipal principal() {
        return new UserPrincipal(callerId, "staff@example.com", organizationId, List.of());
    }

    private Payment payment(BigDecimal amount) {
        Payment payment = new Payment(organizationId, UUID.randomUUID(), amount, Payment.Method.CREDIT_CARD);
        payment.setId(paymentId);
        return payment;
    }

    @Test
    void create_refundWithinPaymentAmount_succeeds() {
        when(paymentService.findOrThrow(organizationId, paymentId)).thenReturn(payment(new BigDecimal("100.00")));
        when(refundRecordRepository.sumActiveAmountByPaymentId(paymentId)).thenReturn(BigDecimal.ZERO);

        RefundRecord refund = service.create(
                principal(), new CreateRefundRecordRequest(paymentId, new BigDecimal("50.00"), RefundRecord.Reason.CUSTOMER_REQUEST, null, null));

        assertThat(refund.getAmount()).isEqualByComparingTo("50.00");
        assertThat(refund.getOwnerId()).isEqualTo(callerId);
    }

    @Test
    void create_refundExceedingRemainingPaymentAmount_throwsExceedsPayment() {
        when(paymentService.findOrThrow(organizationId, paymentId)).thenReturn(payment(new BigDecimal("100.00")));
        when(refundRecordRepository.sumActiveAmountByPaymentId(paymentId)).thenReturn(new BigDecimal("70.00"));

        assertThatThrownBy(() -> service.create(
                        principal(),
                        new CreateRefundRecordRequest(paymentId, new BigDecimal("50.00"), RefundRecord.Reason.CUSTOMER_REQUEST, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("REFUND_RECORD_EXCEEDS_PAYMENT"));
    }

    @Test
    void update_editedAmountExceedingPayment_throwsExceedsPayment() {
        UUID refundId = UUID.randomUUID();
        RefundRecord refund = new RefundRecord(organizationId, paymentId, callerId, new BigDecimal("30.00"), RefundRecord.Reason.BILLING_ERROR);
        refund.setId(refundId);
        when(refundRecordRepository.findActiveByIdAndOrganizationId(refundId, organizationId)).thenReturn(Optional.of(refund));
        when(paymentService.findOrThrow(organizationId, paymentId)).thenReturn(payment(new BigDecimal("100.00")));
        when(refundRecordRepository.sumActiveAmountByPaymentIdExcluding(paymentId, refundId)).thenReturn(new BigDecimal("80.00"));

        assertThatThrownBy(() -> service.update(
                        principal(), refundId, new UpdateRefundRecordRequest(new BigDecimal("30.00"), RefundRecord.Reason.BILLING_ERROR, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("REFUND_RECORD_EXCEEDS_PAYMENT"));
    }

    @Test
    void updateStatus_movingToProcessed_stampsProcessedAtOnce() {
        UUID refundId = UUID.randomUUID();
        RefundRecord refund = new RefundRecord(organizationId, paymentId, callerId, new BigDecimal("30.00"), RefundRecord.Reason.OTHER);
        refund.setId(refundId);
        when(refundRecordRepository.findActiveByIdAndOrganizationId(refundId, organizationId)).thenReturn(Optional.of(refund));

        RefundRecord updated = service.updateStatus(principal(), refundId, RefundRecord.Status.PROCESSED);

        assertThat(updated.getStatus()).isEqualTo(RefundRecord.Status.PROCESSED);
        assertThat(updated.getProcessedAt()).isNotNull();
    }
}
