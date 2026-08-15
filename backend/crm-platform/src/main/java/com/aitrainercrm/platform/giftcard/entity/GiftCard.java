package com.aitrainercrm.platform.giftcard.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A prepaid balance issued to a client - see V54's migration comment for the gap this fills.
 * Owner-scoped, full OWN/TEAM/DEPARTMENT/ORGANIZATION ladder; {@link #contactId} is the
 * recipient client, not the authorization subject - {@code ownerId} (the staff member who issued
 * or manages the card) is what
 * {@link com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService} checks, same
 * split {@code ClientDocument#contactId}/{@code LockerAssignment#contactId} established.
 * {@link #status} is a free (non-linear) state machine - reactivating a cancelled or expired card
 * is a legitimate correction, never blocked. Unlike a plain status flip, redemption deducts from
 * {@link #currentBalance} - see {@code GiftCardService#redeem}. {@link #redeemedAt} is stamped
 * once, the first time {@code currentBalance} reaches zero, and never overwritten afterward.
 */
@Entity
@Table(name = "gift_cards")
@Getter
@Setter
@NoArgsConstructor
public class GiftCard extends BaseEntity {

    public enum Status {
        ACTIVE, REDEEMED, EXPIRED, CANCELLED
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "contact_id", nullable = false)
    private UUID contactId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(name = "initial_balance", nullable = false, precision = 10, scale = 2)
    private BigDecimal initialBalance;

    @Column(name = "current_balance", nullable = false, precision = 10, scale = 2)
    private BigDecimal currentBalance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt = Instant.now();

    @Column(name = "expires_at")
    private LocalDate expiresAt;

    /** Stamped once, the first time currentBalance reaches zero - see this class's javadoc. */
    @Column(name = "redeemed_at")
    private Instant redeemedAt;

    @Column(length = 2000)
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public GiftCard(UUID organizationId, UUID contactId, UUID ownerId, String code, BigDecimal initialBalance) {
        this.organizationId = organizationId;
        this.contactId = contactId;
        this.ownerId = ownerId;
        this.code = code;
        this.initialBalance = initialBalance;
        this.currentBalance = initialBalance;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
