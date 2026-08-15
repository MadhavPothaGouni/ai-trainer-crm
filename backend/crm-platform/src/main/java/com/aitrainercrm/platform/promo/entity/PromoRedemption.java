package com.aitrainercrm.platform.promo.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One client's use of a {@link PromoCode} - see V51's migration comment for the gap this fills.
 * Owner-scoped like {@link com.aitrainercrm.platform.locker.entity.LockerAssignment}, full
 * OWN/TEAM/DEPARTMENT/ORGANIZATION ladder. {@link #contactId} is the client who redeemed the code,
 * not the authorization subject - {@code ownerId} (the staff member who applied it) is what
 * {@link com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService} checks, same
 * split {@code ClientDocument#contactId}/{@code LockerAssignment#contactId} established. Unlike
 * those siblings there is no {@code status} field - a redemption is a point-in-time fact, not a
 * lifecycle, closer to {@code ClassAttendance#registeredAt} than to a free state machine; {@link
 * #redeemedAt} is simply set once at creation. {@link #orderId} is deliberately not a foreign key
 * (see V51's migration comment) to keep this module decoupled from the order module.
 */
@Entity
@Table(name = "promo_redemptions")
@Getter
@Setter
@NoArgsConstructor
public class PromoRedemption extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "promo_code_id", nullable = false)
    private UUID promoCodeId;

    @Column(name = "contact_id", nullable = false)
    private UUID contactId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "redeemed_at", nullable = false)
    private Instant redeemedAt = Instant.now();

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "amount_discounted", precision = 14, scale = 2)
    private BigDecimal amountDiscounted;

    @Column(length = 2000)
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public PromoRedemption(UUID organizationId, UUID promoCodeId, UUID contactId, UUID ownerId) {
        this.organizationId = organizationId;
        this.promoCodeId = promoCodeId;
        this.contactId = contactId;
        this.ownerId = ownerId;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
