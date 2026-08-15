package com.aitrainercrm.platform.loyalty.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One entry in a client's loyalty points ledger - see V59's migration comment for the backstory.
 * There's no stored balance anywhere on this entity or any other; a client's current balance is
 * always the live sum of their non-deleted transactions (see
 * {@code LoyaltyTransactionService#getBalance}). Owner-scoped, same {@code contactId}-is-the-client
 * / {@code ownerId}-is-the-authorization-subject split every other contact-facing occurrence entity
 * in this platform uses. Has no status field - a ledger entry is a point-in-time fact, same shape
 * as {@code ProgressPhoto}/{@code PromoRedemption}.
 */
@Entity
@Table(name = "loyalty_transactions")
@Getter
@Setter
@NoArgsConstructor
public class LoyaltyTransaction extends BaseEntity {

    public enum Reason {
        EARNED_CHECKIN, EARNED_REFERRAL, REDEEMED_REWARD, MANUAL_ADJUSTMENT
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "contact_id", nullable = false)
    private UUID contactId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    /** Signed delta - positive for earned points, negative for spent points. See {@link Reason}'s per-value sign rule, enforced in the service layer. */
    @Column(nullable = false)
    private int points;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Reason reason;

    @Column(length = 2000)
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public LoyaltyTransaction(UUID organizationId, UUID contactId, UUID ownerId, int points, Reason reason) {
        this.organizationId = organizationId;
        this.contactId = contactId;
        this.ownerId = ownerId;
        this.points = points;
        this.reason = reason;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
