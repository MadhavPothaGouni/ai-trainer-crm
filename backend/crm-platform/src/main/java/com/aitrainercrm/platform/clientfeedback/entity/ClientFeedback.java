package com.aitrainercrm.platform.clientfeedback.entity;

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
 * An NPS-style rating (0-10) plus optional comments a client gave about one session, class, or the
 * business in general - see V66's migration comment. Owner-scoped, same "point-in-time fact" shape
 * {@code NutritionLog}/{@code ProgressPhoto} established: no {@code status} field, {@link
 * #submittedAt} is simply set once at creation. {@link #relatedType} distinguishes what the
 * feedback was about without an FK to a specific session row, since GENERAL feedback about the
 * business overall has nothing to point at.
 */
@Entity
@Table(name = "client_feedback")
@Getter
@Setter
@NoArgsConstructor
public class ClientFeedback extends BaseEntity {

    public enum RelatedType {
        SESSION, CLASS, GENERAL
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "contact_id", nullable = false)
    private UUID contactId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "nps_score", nullable = false)
    private Integer npsScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "related_type", nullable = false, length = 20)
    private RelatedType relatedType = RelatedType.GENERAL;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt = Instant.now();

    @Column(length = 2000)
    private String comments;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public ClientFeedback(UUID organizationId, UUID contactId, UUID ownerId, Integer npsScore, RelatedType relatedType, Instant submittedAt) {
        this.organizationId = organizationId;
        this.contactId = contactId;
        this.ownerId = ownerId;
        this.npsScore = npsScore;
        this.relatedType = relatedType;
        this.submittedAt = submittedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
