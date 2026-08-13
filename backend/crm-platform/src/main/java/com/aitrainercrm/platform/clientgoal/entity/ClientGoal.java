package com.aitrainercrm.platform.clientgoal.entity;

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
 * A coach/trainer-defined, freeform measurable objective (weight, strength, endurance, or a
 * custom metric) tracked against one {@link com.aitrainercrm.platform.contact.entity.Contact}
 * over time - the resource this whole module exists to fill in; see V36's migration comment for
 * why CourseEnrollment/SalesGoal/Contract each fall short of this and why this mirrors
 * {@link com.aitrainercrm.platform.contract.entity.Contract}'s owner-scoped shape rather than
 * inventing a new one.
 */
@Entity
@Table(name = "client_goals")
@Getter
@Setter
@NoArgsConstructor
public class ClientGoal extends BaseEntity {

    public enum GoalType {
        WEIGHT_LOSS, STRENGTH, ENDURANCE, CUSTOM
    }

    public enum Status {
        ACTIVE, ON_HOLD, ACHIEVED, ABANDONED
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    /** The client the goal is FOR - never the authorization subject, same "owner and target are different people" split SequenceEnrollment already established. See ClientGoal's javadoc. */
    @Column(name = "contact_id", nullable = false)
    private UUID contactId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "goal_type", nullable = false, length = 20)
    private GoalType goalType = GoalType.CUSTOM;

    /** Free-text label ("lbs", "reps", "5k time") rather than an enum, since a CUSTOM goalType can measure literally anything. */
    @Column(name = "metric_unit", length = 30)
    private String metricUnit;

    @Column(name = "start_value", precision = 10, scale = 2)
    private BigDecimal startValue;

    @Column(name = "target_value", precision = 10, scale = 2)
    private BigDecimal targetValue;

    @Column(name = "current_value", precision = 10, scale = 2)
    private BigDecimal currentValue;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    /** Stamped the first time status moves to ACHIEVED, never overwritten afterward - same "snapshot, don't let it drift" rule Contract#signedAt already documents. */
    @Column(name = "achieved_at")
    private Instant achievedAt;

    @Column(length = 2000)
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public ClientGoal(UUID organizationId, UUID contactId, UUID ownerId, String title) {
        this.organizationId = organizationId;
        this.contactId = contactId;
        this.ownerId = ownerId;
        this.title = title;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
