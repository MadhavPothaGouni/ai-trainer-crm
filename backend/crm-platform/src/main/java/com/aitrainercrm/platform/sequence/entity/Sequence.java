package com.aitrainercrm.platform.sequence.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A reusable, ordered outreach cadence definition - see V32's migration comment for the full
 * module overview. Mirrors {@link com.aitrainercrm.platform.course.entity.Course}'s shape exactly:
 * shared organization data an admin/sales-ops user maintains, no {@code ownerId}, TEAM/DEPARTMENT/
 * ORGANIZATION scope only (no OWN) - {@code SequenceService} does no {@code
 * ScopeAuthorizationService} call, same reasoning {@code CourseService}'s javadoc gives.
 *
 * <p>The actual steps live in {@link SequenceStep}, a real child row (FK cascade delete) rather
 * than a JSON blob or embedded collection - {@code SequenceService} manages the list the same way
 * {@code QuoteService} manages a quote's line items.
 */
@Entity
@Table(name = "sequences")
@Getter
@Setter
@NoArgsConstructor
public class Sequence extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Sequence(UUID organizationId, String name) {
        this.organizationId = organizationId;
        this.name = name;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
