package com.aitrainercrm.platform.timeoff.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A staff member's request for vacation/sick/personal leave - see V49's migration comment for
 * the gap this fills. Owner-scoped like {@link com.aitrainercrm.platform.clientgoal.entity.ClientGoal},
 * full OWN/TEAM/DEPARTMENT/ORGANIZATION ladder; unlike ClientGoal's owner/contact split,
 * {@link #ownerId} here IS the person the request is for - there's no separate target. {@link #status}
 * is a free (non-linear) state machine like every other lifecycle field in this platform -
 * approving a previously-denied request is a legitimate correction, never blocked.
 * {@link #approvedAt} is stamped the first time status moves to APPROVED and never overwritten
 * afterward, same "stamp once" rule {@code Shift#clockInAt} already establishes.
 */
@Entity
@Table(name = "time_off_requests")
@Getter
@Setter
@NoArgsConstructor
public class TimeOffRequest extends BaseEntity {

    public enum Type {
        VACATION, SICK, PERSONAL, UNPAID, OTHER
    }

    public enum Status {
        PENDING, APPROVED, DENIED, CANCELLED
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type = Type.VACATION;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    /** Stamped the first time status moves to APPROVED, never overwritten afterward - see this class's javadoc. */
    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(length = 2000)
    private String reason;

    @Column(length = 2000)
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public TimeOffRequest(UUID organizationId, UUID ownerId, LocalDate startDate, LocalDate endDate) {
        this.organizationId = organizationId;
        this.ownerId = ownerId;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
