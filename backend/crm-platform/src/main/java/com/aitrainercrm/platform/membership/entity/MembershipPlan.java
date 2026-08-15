package com.aitrainercrm.platform.membership.entity;

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
 * A recurring-billing catalog entry ("Unlimited Monthly", "10-Session Pack", "Annual Elite") a
 * client can be subscribed to via {@link Membership}. Same shared-organization-catalog shape as
 * {@link com.aitrainercrm.platform.product.entity.Product} - see V42's migration comment: no
 * {@code ownerId}, TEAM/DEPARTMENT/ORGANIZATION scopes only, no per-record
 * ScopeAuthorizationService check in {@link com.aitrainercrm.platform.membership.service.MembershipPlanService}.
 */
@Entity
@Table(name = "membership_plans")
@Getter
@Setter
@NoArgsConstructor
public class MembershipPlan extends BaseEntity {

    public enum BillingCycle {
        WEEKLY, MONTHLY, QUARTERLY, ANNUAL, ONE_TIME
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 20)
    private BillingCycle billingCycle = BillingCycle.MONTHLY;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    @Column(length = 3)
    private String currency;

    /** Null means unlimited access for the cycle; a non-null value is a fixed number of sessions the plan grants per cycle (e.g. a "10-Session Pack"). */
    @Column(name = "session_credits")
    private Integer sessionCredits;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public MembershipPlan(UUID organizationId, String name) {
        this.organizationId = organizationId;
        this.name = name;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
