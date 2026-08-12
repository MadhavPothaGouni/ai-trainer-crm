package com.aitrainercrm.platform.dedupe.entity;

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
 * A flagged, likely-duplicate pair of Leads, Contacts, or Accounts - created by {@code
 * DuplicateDetectionListener}, resolved by a human via {@code DuplicateMatchService#merge} or
 * {@code #dismiss}. See V23's migration comment for why {@link #recordAId}/{@link #recordBId} are
 * an order-independent pair rather than "primary"/"duplicate" (only {@link #survivorId}/{@link
 * #absorbedId}, populated on merge, carry "which one won" meaning), and for why no permission
 * exists in the catalog for this entirely - {@code DuplicateMatchService} reuses LEAD:UPDATE/
 * CONTACT:UPDATE/ACCOUNT:UPDATE, checked against both records in the pair.
 */
@Entity
@Table(name = "dedupe_matches")
@Getter
@Setter
@NoArgsConstructor
public class DuplicateMatch extends BaseEntity {

    public enum EntityType {
        LEAD, CONTACT, ACCOUNT
    }

    public enum MatchReason {
        EMAIL, NAME
    }

    public enum Status {
        PENDING, MERGED, DISMISSED
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 20)
    private EntityType entityType;

    @Column(name = "record_a_id", nullable = false)
    private UUID recordAId;

    @Column(name = "record_b_id", nullable = false)
    private UUID recordBId;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_reason", nullable = false, length = 20)
    private MatchReason matchReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "survivor_id")
    private UUID survivorId;

    @Column(name = "absorbed_id")
    private UUID absorbedId;

    @Column(name = "resolved_by_user_id")
    private UUID resolvedByUserId;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    public DuplicateMatch(UUID organizationId, EntityType entityType, UUID recordAId, UUID recordBId, MatchReason matchReason) {
        this.organizationId = organizationId;
        this.entityType = entityType;
        this.recordAId = recordAId;
        this.recordBId = recordBId;
        this.matchReason = matchReason;
    }

    /** @param survivorId must be {@link #recordAId} or {@link #recordBId} - DuplicateMatchService validates that before calling this. */
    public void resolveMerged(UUID survivorId, UUID absorbedId, UUID resolvedByUserId) {
        this.status = Status.MERGED;
        this.survivorId = survivorId;
        this.absorbedId = absorbedId;
        this.resolvedByUserId = resolvedByUserId;
        this.resolvedAt = Instant.now();
    }

    public void resolveDismissed(UUID resolvedByUserId) {
        this.status = Status.DISMISSED;
        this.resolvedByUserId = resolvedByUserId;
        this.resolvedAt = Instant.now();
    }
}
