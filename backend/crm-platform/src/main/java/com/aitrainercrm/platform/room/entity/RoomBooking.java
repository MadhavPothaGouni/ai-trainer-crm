package com.aitrainercrm.platform.room.entity;

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
 * One reservation of a {@link Room} for a block of time - see V53's migration comment for the
 * gap this fills. Owner-scoped like {@link com.aitrainercrm.platform.locker.entity.LockerAssignment},
 * full OWN/TEAM/DEPARTMENT/ORGANIZATION ladder. {@link #status} is a free (non-linear) state
 * machine - re-confirming a cancelled booking is a legitimate correction, never blocked. Unlike
 * every prior occurrence entity, this one also carries a real scheduling-conflict rule enforced
 * in {@code RoomBookingService#assertNoOverlap}: a room can't hold two CONFIRMED bookings whose
 * [startsAt, endsAt) windows overlap.
 */
@Entity
@Table(name = "room_bookings")
@Getter
@Setter
@NoArgsConstructor
public class RoomBooking extends BaseEntity {

    public enum Status {
        CONFIRMED, CANCELLED
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 200)
    private String purpose;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.CONFIRMED;

    @Column(length = 2000)
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public RoomBooking(UUID organizationId, UUID roomId, UUID ownerId, String purpose, Instant startsAt, Instant endsAt) {
        this.organizationId = organizationId;
        this.roomId = roomId;
        this.ownerId = ownerId;
        this.purpose = purpose;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
