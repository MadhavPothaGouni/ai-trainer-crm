package com.aitrainercrm.platform.equipment.entity;

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
 * A booking of a specific {@link Equipment} for a time slot - see V56's migration comment for the
 * gap this fills. Owner-scoped like {@link com.aitrainercrm.platform.locker.entity.LockerAssignment},
 * full OWN/TEAM/DEPARTMENT/ORGANIZATION ladder. {@link #contactId} (when present) is the client
 * the reservation is for, not the authorization subject - {@code ownerId} (the staff member who
 * made the reservation) is what
 * {@link com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService} checks.
 * Unlike {@code RoomBooking}, there's no scheduling-conflict check here - see V56's migration
 * comment for why. {@link #status} is a free (non-linear) state machine.
 */
@Entity
@Table(name = "equipment_reservations")
@Getter
@Setter
@NoArgsConstructor
public class EquipmentReservation extends BaseEntity {

    public enum Status {
        CONFIRMED, CANCELLED
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    /** The client the reservation is for - nullable, since equipment can be reserved for internal/staff use too. */
    @Column(name = "contact_id")
    private UUID contactId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

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

    public EquipmentReservation(UUID organizationId, UUID equipmentId, UUID ownerId, Instant startsAt, Instant endsAt) {
        this.organizationId = organizationId;
        this.equipmentId = equipmentId;
        this.ownerId = ownerId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
