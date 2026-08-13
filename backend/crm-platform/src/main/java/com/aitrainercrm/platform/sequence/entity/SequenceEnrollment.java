package com.aitrainercrm.platform.sequence.entity;

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
 * A Lead or Contact being worked through a {@link Sequence} by a rep - a normal owner-scoped CRM
 * record, same shape {@code CourseEnrollment}/{@code UserCertification} use, except here {@link
 * #ownerId} and {@link #targetId} are genuinely two different people: {@code ownerId} is the rep
 * doing the outreach (what every other owner-scoped entity's {@code ownerId} already means),
 * {@code targetId} is the Lead/Contact being worked - see V32's migration comment for why this
 * differs from CourseEnrollment/UserCertification, where the "owner" and the tracked person were
 * the same.
 */
@Entity
@Table(name = "sequence_enrollments")
@Getter
@Setter
@NoArgsConstructor
public class SequenceEnrollment extends BaseEntity {

    public enum TargetType {
        LEAD, CONTACT
    }

    public enum Status {
        ACTIVE, PAUSED, COMPLETED, CANCELLED
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "sequence_id", nullable = false)
    private UUID sequenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private TargetType targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "current_step_index", nullable = false)
    private int currentStepIndex = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public SequenceEnrollment(UUID organizationId, UUID sequenceId, TargetType targetType, UUID targetId, UUID ownerId) {
        this.organizationId = organizationId;
        this.sequenceId = sequenceId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.ownerId = ownerId;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }
}
