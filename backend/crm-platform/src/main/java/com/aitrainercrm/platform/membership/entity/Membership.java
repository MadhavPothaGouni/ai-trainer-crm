package com.aitrainercrm.platform.membership.entity;

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
 * One {@link com.aitrainercrm.platform.contact.entity.Contact}'s actual, recurring subscription
 * to a {@link MembershipPlan} - the resource this whole module exists to fill in; see V42's
 * migration comment for why Order/Invoice/Payment/Contract/SalesGoal each fall short of "this
 * client pays on a recurring cadence for ongoing access." Owner-scoped like
 * {@link com.aitrainercrm.platform.clientgoal.entity.ClientGoal}/
 * {@link com.aitrainercrm.platform.contract.entity.Contract}, not the shared-catalog shape
 * {@link MembershipPlan} itself uses.
 */
@Entity
@Table(name = "memberships")
@Getter
@Setter
@NoArgsConstructor
public class Membership extends BaseEntity {

    public enum Status {
        ACTIVE, PAUSED, CANCELLED, EXPIRED
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    /** The client the membership is FOR - never the authorization subject, same "owner and target are different people" split ClientGoal already established. */
    @Column(name = "contact_id", nullable = false)
    private UUID contactId;

    @Column(name = "membership_plan_id", nullable = false)
    private UUID membershipPlanId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    /** Snapshotted from the plan's price at creation time and never recomputed - see V42's migration comment for why a later plan price change must never retroactively re-bill an existing member. */
    @Column(name = "billing_cycle_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal billingCyclePrice = BigDecimal.ZERO;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    /** Null once CANCELLED/EXPIRED, since there's nothing left to bill. */
    @Column(name = "next_billing_date")
    private LocalDate nextBillingDate;

    @Column(name = "auto_renew", nullable = false)
    private boolean autoRenew = true;

    /** Mirrors the plan's sessionCredits at creation, then decremented as sessions are consumed elsewhere; null if the plan grants unlimited access. */
    @Column(name = "remaining_credits")
    private Integer remainingCredits;

    /** Stamped whenever status moves INTO PAUSED - unlike ClientGoal#achievedAt, this reflects the most recent pause, not the first one, since pausing and resuming repeatedly over a membership's life is normal. */
    @Column(name = "paused_at")
    private Instant pausedAt;

    /** Same "most recent, not first" reasoning as pausedAt - the free status model lets a cancelled membership be reactivated, so this tracks the latest cancellation. */
    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(length = 2000)
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Membership(UUID organizationId, UUID contactId, UUID membershipPlanId, UUID ownerId, LocalDate startDate) {
        this.organizationId = organizationId;
        this.contactId = contactId;
        this.membershipPlanId = membershipPlanId;
        this.ownerId = ownerId;
        this.startDate = startDate;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
