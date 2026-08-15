package com.aitrainercrm.platform.promo.entity;

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
 * A discount code the organization's clients can redeem - see V51's migration comment for the gap
 * this fills. Shared-organization-catalog shape like
 * {@link com.aitrainercrm.platform.locker.entity.Locker}: no {@code ownerId},
 * TEAM/DEPARTMENT/ORGANIZATION scopes only.
 */
@Entity
@Table(name = "promo_codes")
@Getter
@Setter
@NoArgsConstructor
public class PromoCode extends BaseEntity {

    public enum DiscountType {
        PERCENTAGE, FIXED_AMOUNT
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType = DiscountType.PERCENTAGE;

    @Column(name = "discount_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "max_redemptions")
    private Integer maxRedemptions;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "expires_at")
    private LocalDate expiresAt;

    @Column(length = 2000)
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public PromoCode(UUID organizationId, String code, BigDecimal discountValue) {
        this.organizationId = organizationId;
        this.code = code;
        this.discountValue = discountValue;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
