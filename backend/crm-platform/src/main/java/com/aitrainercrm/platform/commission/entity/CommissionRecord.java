package com.aitrainercrm.platform.commission.entity;

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
 * A locked-in commission owed for exactly one Opportunity, created once by {@code
 * CommissionEngine} the moment that Opportunity's persisted stage is read as {@code CLOSED_WON} -
 * never recomputed, never regenerated. See V29's migration comment for why this is materialized
 * rather than computed live the way {@code SalesGoal} progress is: a commission is money owed, and
 * a later change to the originating {@link CommissionPlan}'s rate must never retroactively change
 * what a rep already earned on a deal they already closed. {@link #dealAmount}, {@link #rateType},
 * and {@link #rate} are a frozen copy of the plan's numbers at the moment this record was created,
 * not a live join back to {@link CommissionPlan} - {@link #planId} is kept purely for traceability
 * ("which plan produced this"), never read by any calculation.
 */
@Entity
@Table(name = "commission_records")
@Getter
@Setter
@NoArgsConstructor
public class CommissionRecord extends BaseEntity {

    public enum Status {
        PENDING, APPROVED, PAID
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "opportunity_id", nullable = false)
    private UUID opportunityId;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "plan_id")
    private UUID planId;

    @Column(name = "deal_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal dealAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "rate_type", nullable = false, length = 20)
    private CommissionPlan.RateType rateType;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal rate;

    @Column(name = "commission_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal commissionAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "earned_at", nullable = false)
    private Instant earnedAt = Instant.now();

    @Column(name = "paid_at")
    private Instant paidAt;

    public CommissionRecord(
            UUID organizationId, UUID opportunityId, UUID ownerUserId, UUID planId, BigDecimal dealAmount,
            CommissionPlan.RateType rateType, BigDecimal rate, BigDecimal commissionAmount) {
        this.organizationId = organizationId;
        this.opportunityId = opportunityId;
        this.ownerUserId = ownerUserId;
        this.planId = planId;
        this.dealAmount = dealAmount;
        this.rateType = rateType;
        this.rate = rate;
        this.commissionAmount = commissionAmount;
    }
}
