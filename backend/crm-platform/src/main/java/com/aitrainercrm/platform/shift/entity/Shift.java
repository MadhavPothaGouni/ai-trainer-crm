package com.aitrainercrm.platform.shift.entity;

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
 * One employee's actual scheduled shift on one date - owner-scoped like
 * {@link com.aitrainercrm.platform.membership.entity.Membership}/{@link com.aitrainercrm.platform.groupclass.entity.ClassSession},
 * full OWN/TEAM/DEPARTMENT/ORGANIZATION ladder. {@link #shiftTemplateId} is nullable - a shift
 * can be scheduled ad-hoc without tracing back to a recurring {@link ShiftTemplate}.
 * {@link #status} is a free state machine like every other lifecycle field in this platform;
 * {@link #clockInAt}/{@link #clockOutAt} are stamped once (the first time each is set) and never
 * overwritten by a later correction - same rule {@code Contract#signedAt} established.
 */
@Entity
@Table(name = "shifts")
@Getter
@Setter
@NoArgsConstructor
public class Shift extends BaseEntity {

    public enum Status {
        SCHEDULED, IN_PROGRESS, COMPLETED, MISSED, CANCELLED
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "shift_template_id")
    private UUID shiftTemplateId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "shift_date", nullable = false)
    private LocalDate shiftDate;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.SCHEDULED;

    /** Stamped the first time it's set - never overwritten if the shift is later corrected. */
    @Column(name = "clock_in_at")
    private Instant clockInAt;

    @Column(name = "clock_out_at")
    private Instant clockOutAt;

    @Column(length = 2000)
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Shift(UUID organizationId, UUID ownerId, LocalDate shiftDate, Instant startsAt, Instant endsAt) {
        this.organizationId = organizationId;
        this.ownerId = ownerId;
        this.shiftDate = shiftDate;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
