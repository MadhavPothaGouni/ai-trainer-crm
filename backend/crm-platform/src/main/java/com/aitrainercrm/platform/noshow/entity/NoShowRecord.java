package com.aitrainercrm.platform.noshow.entity;

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
 * A client missing a scheduled booking without cancelling - see V58's migration comment for the
 * backstory. Owner-scoped, same {@code contactId}-is-the-client / {@code ownerId}-is-the-
 * authorization-subject split every other contact-facing occurrence entity in this platform uses
 * (e.g. {@code ClientCheckIn}, {@code ProgressPhoto}). Has no status field and no PATCH
 * {@code .../status} endpoint - {@code waived}/{@code waivedAt} is the only lifecycle state this
 * entity carries, and it's flipped exactly once via the dedicated
 * {@code NoShowRecordService#waive} action, never directly settable through create/update.
 */
@Entity
@Table(name = "no_show_records")
@Getter
@Setter
@NoArgsConstructor
public class NoShowRecord extends BaseEntity {

    public enum RelatedType {
        CLASS_SESSION, TRAINING_SESSION, OTHER
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "contact_id", nullable = false)
    private UUID contactId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "related_type", nullable = false, length = 20)
    private RelatedType relatedType = RelatedType.OTHER;

    @Column(name = "fee_amount", precision = 10, scale = 2)
    private BigDecimal feeAmount;

    /** Flipped exactly once by {@code NoShowRecordService#waive} - never set directly by create/update. */
    @Column(nullable = false)
    private boolean waived = false;

    /** Stamped once, the first (and only) time {@code waived} flips to true - same "stamp once" rule every other lifecycle timestamp in this platform follows. */
    @Column(name = "waived_at")
    private Instant waivedAt;

    @Column(length = 2000)
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public NoShowRecord(UUID organizationId, UUID contactId, UUID ownerId, Instant occurredAt) {
        this.organizationId = organizationId;
        this.contactId = contactId;
        this.ownerId = ownerId;
        this.occurredAt = occurredAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
