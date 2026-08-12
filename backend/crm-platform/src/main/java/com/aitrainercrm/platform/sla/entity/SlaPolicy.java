package com.aitrainercrm.platform.sla.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import com.aitrainercrm.platform.ticket.entity.Ticket;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * "URGENT tickets get a first response within 30 minutes and must be resolved within 4 hours; if
 * either deadline passes, notify this manager." Reuses {@link Ticket.Priority} directly rather
 * than declaring a parallel enum - same cross-module reuse SlaEvaluationService already needs to
 * read {@code Ticket#getPriority()}/{@code #getStatus()}/{@code #getResolvedAt()} anyway, so a
 * second enum with the same four values would just be a drift risk with no real independence
 * benefit (unlike {@link com.aitrainercrm.platform.approval.entity.ApprovalRequest.RelatedToType},
 * which deliberately does NOT reuse {@code CrmRecordType}-shaped types because its three values
 * are a genuinely different set).
 *
 * <p>Entirely gated by {@code SLA_POLICY:*:ORGANIZATION} - see V20's migration comment for why
 * this has no {@code ownerId}/OWN/TEAM/DEPARTMENT variant, same shape {@code CustomField}/
 * {@code ApiKey}/{@code WebhookSubscription} already use for org-wide admin configuration.
 */
@Entity
@Table(name = "sla_policies")
@Getter
@Setter
@NoArgsConstructor
public class SlaPolicy extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Ticket.Priority priority;

    @Column(name = "response_target_minutes", nullable = false)
    private int responseTargetMinutes;

    @Column(name = "resolution_target_minutes", nullable = false)
    private int resolutionTargetMinutes;

    /** Nullable - a policy can exist purely to track breaches (visible on the ticket) without notifying anyone. */
    @Column(name = "escalate_to_user_id")
    private UUID escalateToUserId;

    @Column(nullable = false)
    private boolean active = true;

    public SlaPolicy(UUID organizationId, String name, Ticket.Priority priority, int responseTargetMinutes, int resolutionTargetMinutes) {
        this.organizationId = organizationId;
        this.name = name;
        this.priority = priority;
        this.responseTargetMinutes = responseTargetMinutes;
        this.resolutionTargetMinutes = resolutionTargetMinutes;
    }
}
