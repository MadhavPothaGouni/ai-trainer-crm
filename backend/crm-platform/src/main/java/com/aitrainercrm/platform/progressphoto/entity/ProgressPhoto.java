package com.aitrainercrm.platform.progressphoto.entity;

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
 * A client's physical-progress photo, logged at a point in time - see V55's migration comment for
 * the gap this fills. Owner-scoped like {@link com.aitrainercrm.platform.locker.entity.LockerAssignment};
 * {@link #contactId} is the client photographed, not the authorization subject - {@code ownerId}
 * (the coach who logged it) is what
 * {@link com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService} checks.
 * Unlike most owner-scoped siblings, there is no {@code status} field - same "point-in-time fact"
 * shape {@code PromoRedemption} established: a photo is a fact that was taken, not a lifecycle.
 * {@link #takenAt} is simply set once at creation.
 */
@Entity
@Table(name = "progress_photos")
@Getter
@Setter
@NoArgsConstructor
public class ProgressPhoto extends BaseEntity {

    public enum Category {
        FRONT, SIDE, BACK, OTHER
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "contact_id", nullable = false)
    private UUID contactId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "photo_url", nullable = false, length = 1000)
    private String photoUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category = Category.OTHER;

    @Column(name = "taken_at", nullable = false)
    private Instant takenAt = Instant.now();

    @Column(length = 2000)
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public ProgressPhoto(UUID organizationId, UUID contactId, UUID ownerId, String photoUrl) {
        this.organizationId = organizationId;
        this.contactId = contactId;
        this.ownerId = ownerId;
        this.photoUrl = photoUrl;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
