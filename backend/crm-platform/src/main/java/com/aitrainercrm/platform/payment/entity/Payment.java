package com.aitrainercrm.platform.payment.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single payment recorded against exactly one
 * {@link com.aitrainercrm.platform.invoice.entity.Invoice} (see
 * {@link #invoiceId}). Unlike Order/Invoice, a Payment has no line items and
 * no multi-step status lifecycle of its own - it's an append-mostly ledger
 * entry, not a document that gets built up and edited. {@code
 * PaymentService#record} recomputes the parent invoice's {@code amountPaid}
 * (and flips it to PAID once fully covered) every time one of these is
 * created; {@code PaymentService#delete} does the same in reverse - see
 * {@code InvoiceService#applyAmountPaid}.
 *
 * <p>Soft-deleted like every other entity here (see {@link #deletedAt}) -
 * "we recorded this payment by mistake" needs to un-apply it from the
 * invoice's {@code amountPaid} without losing the record that it was ever
 * entered and then reversed.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment extends BaseEntity {

    public enum Method {
        CREDIT_CARD, BANK_TRANSFER, CASH, CHECK, OTHER
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Method method;

    /** External transaction id / check number / whatever the payer's own receipt calls it - free text, optional. */
    @Column(length = 200)
    private String reference;

    @Column(name = "paid_at", nullable = false)
    private Instant paidAt = Instant.now();

    @Column(length = 1000)
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Payment(UUID organizationId, UUID invoiceId, BigDecimal amount, Method method) {
        this.organizationId = organizationId;
        this.invoiceId = invoiceId;
        this.amount = amount;
        this.method = method;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
