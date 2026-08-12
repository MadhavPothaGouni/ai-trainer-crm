package com.aitrainercrm.platform.salesgoals.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A revenue or deal-count quota for one period, assigned to exactly one of {@link #ownerUserId}
 * (an individual rep) or {@link #teamId} (every current member of that team, summed together) -
 * see V25's migration comment for the constraint and for why progress against a goal is always
 * computed live rather than materialized the way {@code PipelineSnapshot} is.
 *
 * <p>{@link #targetValue} is a single numeric column doing double duty for both {@link Metric}
 * values - a dollar amount for {@code REVENUE}, a whole number of won deals for {@code
 * DEAL_COUNT} - rather than two separate nullable columns, since exactly one is ever meaningful
 * per row and {@code SalesGoalService} already has to switch on {@link #metric} everywhere else.
 */
@Entity
@Table(name = "sales_goals")
@Getter
@Setter
@NoArgsConstructor
public class SalesGoal extends BaseEntity {

    public enum Metric {
        REVENUE, DEAL_COUNT
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
    @Column(nullable = false, length = 20)
    private Metric metric;

    @Column(name = "target_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal targetValue;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    public SalesGoal(UUID organizationId, String name, Metric metric, BigDecimal targetValue, LocalDate periodStart, LocalDate periodEnd) {
        this.organizationId = organizationId;
        this.name = name;
        this.metric = metric;
        this.targetValue = targetValue;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
    }

    public boolean isTeamGoal() {
        return teamId != null;
    }
}
