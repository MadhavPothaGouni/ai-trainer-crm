package com.aitrainercrm.platform.approval.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A named, ordered chain of sign-offs requested on a Quote/Order/
 * Opportunity - see V19's migration comment for why this is a genuinely
 * different concept from Order/Invoice's existing single-permission-gated
 * {@code APPROVE} status transitions, not a duplicate of them.
 *
 * <p>Owner-scoped off {@link #requestedByUserId} via {@code
 * ScopeAuthorizationService}, same OWN/TEAM/DEPARTMENT/ORGANIZATION ladder
 * every owner-scoped module uses for its list/get - <b>except</b> {@code
 * ApprovalRequestService} adds one explicit carve-out on top: whoever is
 * named as the approver on any {@link ApprovalStep} of a request can always
 * read that request and act on their own step, full stop, regardless of
 * what scope they hold. See {@code ApprovalRequestService}'s javadoc for
 * the full reasoning - this is the platform's fifth resource-access shape.
 *
 * <p>No {@code deletedAt} - see V19's migration comment for why {@link
 * #status} reaching CANCELLED/APPROVED/REJECTED already carries the "this
 * is done" meaning a soft-delete column would otherwise exist to capture.
 */
@Entity
@Table(name = "approval_requests")
@Getter
@Setter
@NoArgsConstructor
public class ApprovalRequest extends BaseEntity {

    public enum RelatedToType {
        QUOTE, ORDER, OPPORTUNITY
    }

    public enum Status {
        PENDING, APPROVED, REJECTED, CANCELLED
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "related_to_type", nullable = false, length = 20)
    private RelatedToType relatedToType;

    @Column(name = "related_to_id", nullable = false)
    private UUID relatedToId;

    @Column(name = "requested_by_user_id", nullable = false)
    private UUID requestedByUserId;

    @Column(nullable = false, length = 300)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    /** 1-based - matches ApprovalStep#stepNumber. The step currently awaiting a decision; nothing past it is actionable yet. */
    @Column(name = "current_step_number", nullable = false)
    private int currentStepNumber = 1;

    /** Stamped when status leaves PENDING (approved, rejected, or cancelled) - null while still in flight. */
    @Column(name = "decided_at")
    private Instant decidedAt;

    public ApprovalRequest(UUID organizationId, RelatedToType relatedToType, UUID relatedToId, UUID requestedByUserId, String title) {
        this.organizationId = organizationId;
        this.relatedToType = relatedToType;
        this.relatedToId = relatedToId;
        this.requestedByUserId = requestedByUserId;
        this.title = title;
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }
}
