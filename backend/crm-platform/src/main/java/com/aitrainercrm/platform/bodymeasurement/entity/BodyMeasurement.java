package com.aitrainercrm.platform.bodymeasurement.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A periodic, point-in-time snapshot of a client's physical stats (weight, body fat %, key
 * circumferences) recorded by a coach against one {@link com.aitrainercrm.platform.contact.entity.Contact}
 * - see V41's migration comment for why this is distinct from {@link
 * com.aitrainercrm.platform.clientgoal.entity.ClientGoal} (a single named objective row, not a
 * time series) and every other existing module. Append-only log entry, deliberately no status
 * field - same reasoning {@link com.aitrainercrm.platform.attachment.entity.Attachment} has none.
 */
@Entity
@Table(name = "body_measurements")
@Getter
@Setter
@NoArgsConstructor
public class BodyMeasurement extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    /** The client measured - never the authorization subject, same "owner and target are different people" split ClientGoal/TrainingSession/NutritionPlan already established. */
    @Column(name = "contact_id", nullable = false)
    private UUID contactId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    /** The date of the check-in - distinct from {@code createdAt}, since a coach may backfill an entry for a check-in that happened a few days ago. */
    @Column(name = "measured_at", nullable = false)
    private LocalDate measuredAt;

    @Column(name = "weight_value", precision = 6, scale = 2)
    private BigDecimal weightValue;

    @Column(name = "weight_unit", length = 10)
    private String weightUnit;

    @Column(name = "body_fat_percent", precision = 5, scale = 2)
    private BigDecimal bodyFatPercent;

    @Column(name = "chest_cm", precision = 6, scale = 2)
    private BigDecimal chestCm;

    @Column(name = "waist_cm", precision = 6, scale = 2)
    private BigDecimal waistCm;

    @Column(name = "hips_cm", precision = 6, scale = 2)
    private BigDecimal hipsCm;

    @Column(length = 2000)
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public BodyMeasurement(UUID organizationId, UUID contactId, UUID ownerId, LocalDate measuredAt) {
        this.organizationId = organizationId;
        this.contactId = contactId;
        this.ownerId = ownerId;
        this.measuredAt = measuredAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
