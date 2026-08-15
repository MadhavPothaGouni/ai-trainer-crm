package com.aitrainercrm.platform.equipment.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One maintenance event performed on a piece of {@link Equipment} - see V44's migration comment
 * for why this has no status field of its own: it's a record of something that already happened,
 * not a record with a lifecycle. Owner-scoped like {@link com.aitrainercrm.platform.membership.entity.Membership}/
 * {@link com.aitrainercrm.platform.clientgoal.entity.ClientGoal}, full OWN/TEAM/DEPARTMENT/
 * ORGANIZATION ladder; {@link #ownerId} is whoever performed or logged the work, defaulting to
 * the caller via the usual {@code resolveOwner} pattern.
 */
@Entity
@Table(name = "maintenance_logs")
@Getter
@Setter
@NoArgsConstructor
public class MaintenanceLog extends BaseEntity {

    public enum Type {
        ROUTINE, REPAIR, INSPECTION, CLEANING
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "performed_at", nullable = false)
    private Instant performedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type = Type.ROUTINE;

    @Column(precision = 14, scale = 2)
    private BigDecimal cost;

    @Column(length = 2000)
    private String notes;

    /** Set by hand by whoever logged the work - this module doesn't attempt to auto-schedule follow-up maintenance. */
    @Column(name = "next_due_date")
    private LocalDate nextDueDate;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public MaintenanceLog(UUID organizationId, UUID equipmentId, UUID ownerId, Instant performedAt) {
        this.organizationId = organizationId;
        this.equipmentId = equipmentId;
        this.ownerId = ownerId;
        this.performedAt = performedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
