package com.aitrainercrm.platform.groupclass.entity;

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
 * One scheduled occurrence of a {@link GroupClass} - "Spin 45 on Tuesday at 6am" as opposed to
 * the class type itself. Owner-scoped like {@link com.aitrainercrm.platform.membership.entity.Membership}/
 * {@link com.aitrainercrm.platform.clientgoal.entity.ClientGoal}, full OWN/TEAM/DEPARTMENT/
 * ORGANIZATION ladder. {@link #ownerId} here is the instructor actually running the session
 * (defaults to the caller via the same {@code resolveOwner} pattern every other owner-scoped
 * module uses) - named {@code ownerId} rather than {@code instructorId} to keep it a drop-in
 * match for {@code ScopeAuthorizationService}, even though "instructor" is the more natural
 * domain word. {@link #status} is a free state machine like tickets.status/memberships.status -
 * un-cancelling a session is a normal correction (the instructor was sick, then recovered),
 * never blocked by a transition table.
 */
@Entity
@Table(name = "class_sessions")
@Getter
@Setter
@NoArgsConstructor
public class ClassSession extends BaseEntity {

    public enum Status {
        SCHEDULED, CANCELLED, COMPLETED
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "group_class_id", nullable = false)
    private UUID groupClassId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    /** Null means "use the parent GroupClass's capacity"; a non-null value (including 0) overrides it for just this occurrence. */
    @Column(name = "capacity_override")
    private Integer capacityOverride;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.SCHEDULED;

    @Column(length = 2000)
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public ClassSession(UUID organizationId, UUID groupClassId, UUID ownerId, Instant startsAt, Instant endsAt) {
        this.organizationId = organizationId;
        this.groupClassId = groupClassId;
        this.ownerId = ownerId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
