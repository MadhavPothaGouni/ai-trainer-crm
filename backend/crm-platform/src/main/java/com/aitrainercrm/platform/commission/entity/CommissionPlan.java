package com.aitrainercrm.platform.commission.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An admin-defined commission rate assigned to exactly one of an individual user or a team - the
 * same "exactly one of two" shape {@code SalesGoal} (V25), {@code TerritoryRule}, and {@code
 * CustomField} all established before it, backed by a real {@code chk_commission_plans_exactly_one_target}
 * CHECK constraint (V29) rather than an application-only check. See V29's migration comment for why
 * {@link com.aitrainercrm.platform.commission.entity.CommissionRecord} snapshots this plan's rate
 * at close time instead of referencing it live.
 */
@Entity
@Table(name = "commission_plans")
@Getter
@Setter
@NoArgsConstructor
public class CommissionPlan extends BaseEntity {

    public enum RateType {
        /** rate is a percentage of the deal's amount, e.g. 5.00 means 5%. */
        PERCENTAGE,
        /** rate is a flat dollar amount per closed deal, regardless of the deal's amount. */
        FLAT_PER_DEAL
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(name = "team_id")
    private UUID teamId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rate_type", nullable = false, length = 20)
    private RateType rateType;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal rate;

    @Column(nullable = false)
    private boolean active = true;

    public CommissionPlan(UUID organizationId, String name, UUID ownerUserId, UUID teamId, RateType rateType, BigDecimal rate) {
        this.organizationId = organizationId;
        this.name = name;
        this.ownerUserId = ownerUserId;
        this.teamId = teamId;
        this.rateType = rateType;
        this.rate = rate;
    }

    public boolean isTeamPlan() {
        return teamId != null;
    }
}
