package com.aitrainercrm.platform.activity.entity;

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
 * A logged interaction against exactly one Account/Contact/Opportunity/Lead -
 * a call, an email, a meeting, a task, or a freeform note. Unlike those four
 * entities, an Activity isn't itself referenced by anything else, so it gets
 * a real delete rather than the deletedAt soft-delete pattern they use.
 *
 * <p>{@link #relatedToId} has no JPA relationship or DB foreign key - see
 * V4's migration comment for why a single column can't target four different
 * tables. {@link com.aitrainercrm.platform.activity.service.ActivityService}
 * resolves and validates it explicitly against whichever repository
 * {@link #relatedToType} names.
 */
@Entity
@Table(name = "activities")
@Getter
@Setter
@NoArgsConstructor
public class Activity extends BaseEntity {

    public enum Type {
        CALL, EMAIL, MEETING, TASK, NOTE
    }

    public enum Status {
        OPEN, COMPLETED
    }

    public enum Priority {
        LOW, MEDIUM, HIGH
    }

    public enum RelatedToType {
        ACCOUNT, CONTACT, OPPORTUNITY, LEAD
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Priority priority;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "related_to_type", nullable = false, length = 20)
    private RelatedToType relatedToType;

    @Column(name = "related_to_id", nullable = false)
    private UUID relatedToId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    public Activity(UUID organizationId, Type type, String subject, RelatedToType relatedToType, UUID relatedToId, UUID ownerId) {
        this.organizationId = organizationId;
        this.type = type;
        this.subject = subject;
        this.relatedToType = relatedToType;
        this.relatedToId = relatedToId;
        this.ownerId = ownerId;
    }

    public boolean isCompleted() {
        return status == Status.COMPLETED;
    }
}
