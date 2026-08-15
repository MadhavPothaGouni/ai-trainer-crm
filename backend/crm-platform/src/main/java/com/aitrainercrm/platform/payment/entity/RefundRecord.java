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
 * A refund issued against a {@link Payment} - see V65's migration comment for the backstory.
 * Unlike {@link Payment} itself (a shared-org resource), this is owner-scoped - full
 * OWN/TEAM/DEPARTMENT/ORGANIZATION ladder, same shape every other occurrence entity in this
 * platform uses. {@link #status} is a free state machine (REQUESTED/APPROVED/PROCESSED);
 * {@link #processedAt} is stamped once, the first time status moves to PROCESSED.
 */
@Entity
@Table(name = "refund_records")
@Getter
@Setter
@NoArgsConstructor
public class RefundRecord extends BaseEntity {

    public enum Reason {
        CUSTOMER_REQUEST, BILLING_ERROR, SERVICE_ISSUE, OTHER
    }

    public enum Status {
        REQUESTED, APPROVED, PROCESSED
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Reason reason = Reason.OTHER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.REQUESTED;

    /** Stamped once, the first time status moves to PROCESSED - never overwritten afterward. */
    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(length = 2000)
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public RefundRecord(UUID organizationId, UUID paymentId, UUID ownerId, BigDecimal amount, Reason reason) {
        this.organizationId = organizationId;
        this.paymentId = paymentId;
        this.ownerId = ownerId;
        this.amount = amount;
        this.reason = reason;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
