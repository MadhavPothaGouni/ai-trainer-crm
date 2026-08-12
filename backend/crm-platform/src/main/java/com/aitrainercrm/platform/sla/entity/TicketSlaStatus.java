package com.aitrainercrm.platform.sla.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row per ticket that has ever matched an active {@link SlaPolicy}, created lazily by
 * {@code SlaEvaluationService#evaluate} the first time that ticket is evaluated (on a live GET,
 * or by the periodic sweep) - not eagerly at ticket-creation time. See V20's migration comment
 * for why this table has no foreign key to {@code tickets} and why {@code sla_policy_id} is
 * captured once and never re-derived if the policy's targets change later.
 *
 * <p>{@link #responseBreachedAt}/{@link #resolutionBreachedAt}/{@link #escalatedAt} are
 * first-occurrence timestamps, not booleans, and are never cleared once set - a breach that
 * already happened stays on the record even if the underlying ticket is later reopened.
 */
@Entity
@Table(name = "ticket_sla_statuses")
@Getter
@Setter
@NoArgsConstructor
public class TicketSlaStatus extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "ticket_id", nullable = false, unique = true)
    private UUID ticketId;

    @Column(name = "sla_policy_id", nullable = false)
    private UUID slaPolicyId;

    @Column(name = "response_due_at", nullable = false)
    private Instant responseDueAt;

    @Column(name = "resolution_due_at", nullable = false)
    private Instant resolutionDueAt;

    @Column(name = "response_breached_at")
    private Instant responseBreachedAt;

    @Column(name = "resolution_breached_at")
    private Instant resolutionBreachedAt;

    /** Set the first time either deadline is breached AND the policy has an escalateToUserId - see SlaEvaluationService#evaluate. Guards against sending the same escalation notification on every sweep tick. */
    @Column(name = "escalated_at")
    private Instant escalatedAt;

    public TicketSlaStatus(UUID organizationId, UUID ticketId, UUID slaPolicyId, Instant responseDueAt, Instant resolutionDueAt) {
        this.organizationId = organizationId;
        this.ticketId = ticketId;
        this.slaPolicyId = slaPolicyId;
        this.responseDueAt = responseDueAt;
        this.resolutionDueAt = resolutionDueAt;
    }

    public boolean isResponseBreached() {
        return responseBreachedAt != null;
    }

    public boolean isResolutionBreached() {
        return resolutionBreachedAt != null;
    }

    public boolean isEscalated() {
        return escalatedAt != null;
    }
}
