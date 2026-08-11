package com.aitrainercrm.platform.payment.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.invoice.entity.Invoice;
import com.aitrainercrm.platform.invoice.service.InvoiceService;
import com.aitrainercrm.platform.payment.dto.CreatePaymentRequest;
import com.aitrainercrm.platform.payment.entity.Payment;
import com.aitrainercrm.platform.payment.repository.PaymentRepository;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payments recorded against invoices. Same shared-org-resource pattern as
 * Order/Invoice/Product - no {@code ScopeAuthorizationService} calls; the
 * controller's static {@code @PreAuthorize} on {@code PAYMENT:*} is the
 * whole authorization story, entirely independent of whatever permissions
 * the caller holds on {@code INVOICE:*} (recording a payment is a
 * {@code PAYMENT:CREATE} action, not an {@code INVOICE:UPDATE} one).
 *
 * <p>Every mutation here recomputes the parent invoice's rollup via {@link
 * InvoiceService#applyAmountPaid} from the full active-payments ledger
 * ({@link PaymentRepository#sumActiveAmountByInvoiceId}) rather than
 * incrementing/decrementing in place - simpler to reason about and immune to
 * drift if a payment is ever edited in place in the future (nothing does
 * that today; payments are create-or-soft-delete only).
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final InvoiceService invoiceService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<Payment> list(UserPrincipal principal, UUID invoiceId, Pageable pageable) {
        invoiceService.get(principal, invoiceId); // validates the invoice exists in this org
        return paymentRepository.findByOrganizationIdAndInvoiceIdAndDeletedAtIsNullOrderByPaidAtDesc(
                principal.getOrganizationId(), invoiceId, pageable);
    }

    @Transactional(readOnly = true)
    public Payment get(UserPrincipal principal, UUID paymentId) {
        return findOrThrow(principal.getOrganizationId(), paymentId);
    }

    /** Only against a SENT or OVERDUE invoice - not DRAFT (nothing to pay yet, see InvoiceService#issue) and not VOID or PAID. */
    @Transactional
    public Payment record(UserPrincipal principal, UUID invoiceId, CreatePaymentRequest request) {
        Invoice invoice = invoiceService.get(principal, invoiceId);
        if (invoice.getStatus() != Invoice.Status.SENT && invoice.getStatus() != Invoice.Status.OVERDUE) {
            throw new BusinessException(
                    "PAYMENT_INVALID_INVOICE_STATUS",
                    "Payments can only be recorded against a SENT or OVERDUE invoice (was " + invoice.getStatus() + ")",
                    HttpStatus.CONFLICT);
        }

        Payment payment = new Payment(principal.getOrganizationId(), invoiceId, request.amount(), request.method());
        payment.setReference(request.reference());
        payment.setNotes(request.notes());
        if (request.paidAt() != null) {
            payment.setPaidAt(request.paidAt());
        }
        paymentRepository.save(payment);

        applyToInvoice(principal.getOrganizationId(), invoiceId);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "Payment", payment.getId()));
        return payment;
    }

    /** Soft-deletes the payment ("we recorded this by mistake") and re-syncs the invoice's amountPaid/status - see InvoiceService#applyAmountPaid. */
    @Transactional
    public void delete(UserPrincipal principal, UUID paymentId) {
        Payment payment = findOrThrow(principal.getOrganizationId(), paymentId);

        payment.setDeletedAt(Instant.now());
        paymentRepository.save(payment);

        applyToInvoice(principal.getOrganizationId(), payment.getInvoiceId());

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "Payment", paymentId));
    }

    private void applyToInvoice(UUID organizationId, UUID invoiceId) {
        BigDecimal amountPaid = paymentRepository.sumActiveAmountByInvoiceId(invoiceId);
        invoiceService.applyAmountPaid(organizationId, invoiceId, amountPaid);
    }

    private Payment findOrThrow(UUID organizationId, UUID paymentId) {
        return paymentRepository.findActiveByIdAndOrganizationId(paymentId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
    }
}
