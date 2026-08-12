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
 * One named approver's slot in an {@link ApprovalRequest}'s ordered chain.
 * {@link #organizationId} is denormalized from the parent request purely so
 * the "my pending approvals" query (ApprovalStepRepository's whole reason
 * for existing) can filter directly on (organizationId, approverUserId,
 * status) without a join on every request - see V19's migration comment.
 * {@code ApprovalRequestService} keeps it in sync at creation time; nothing
 * ever updates it afterward.
 */
@Entity
@Table(name = "approval_steps")
@Getter
@Setter
@NoArgsConstructor
public class ApprovalStep extends BaseEntity {

    public enum Status {
        PENDING, APPROVED, REJECTED
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "approval_request_id", nullable = false)
    private UUID approvalRequestId;

    /** 1-based, unique per approvalRequestId (see V19's uq_approval_steps_request_step index). */
    @Column(name = "step_number", nullable = false)
    private int stepNumber;

    @Column(name = "approver_user_id", nullable = false)
    private UUID approverUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(length = 1000)
    private String comment;

    @Column(name = "decided_at")
    private Instant decidedAt;

    public ApprovalStep(UUID organizationId, UUID approvalRequestId, int stepNumber, UUID approverUserId) {
        this.organizationId = organizationId;
        this.approvalRequestId = approvalRequestId;
        this.stepNumber = stepNumber;
        this.approverUserId = approverUserId;
    }
}
