package com.aitrainercrm.platform.exercise.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A client's best-ever result for one {@link Exercise}, in one of four {@link RecordType}s - see
 * V64's migration comment for the backstory. Owner-scoped, same shape every other occurrence
 * entity in this platform uses. All four {@link RecordType} values share "higher is better"
 * semantics, so {@code PersonalRecordService#assertIsImprovement}'s single {@code value >
 * currentBest} comparison is universally correct - see the migration comment for why that was a
 * deliberate design choice.
 */
@Entity
@Table(name = "personal_records")
@Getter
@Setter
@NoArgsConstructor
public class PersonalRecord extends BaseEntity {

    public enum RecordType {
        ONE_REP_MAX, MAX_REPS, MAX_WEIGHT, MAX_DURATION_SECONDS
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "contact_id", nullable = false)
    private UUID contactId;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_type", nullable = false, length = 30)
    private RecordType recordType;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal value;

    @Column(name = "achieved_at", nullable = false)
    private Instant achievedAt = Instant.now();

    @Column(length = 2000)
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public PersonalRecord(UUID organizationId, UUID contactId, UUID exerciseId, UUID ownerId, RecordType recordType, BigDecimal value) {
        this.organizationId = organizationId;
        this.contactId = contactId;
        this.exerciseId = exerciseId;
        this.ownerId = ownerId;
        this.recordType = recordType;
        this.value = value;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
