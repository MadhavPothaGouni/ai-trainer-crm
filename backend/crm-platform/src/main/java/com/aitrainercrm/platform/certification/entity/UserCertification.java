package com.aitrainercrm.platform.certification.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One person actually holding a {@link Certification} - the owner-scoped half of this pair, same
 * shape {@code CourseEnrollment} uses for the same reason (see V31's migration comment): {@link
 * #userId} plays the role every other {@code isCoreCrmResource} entity calls {@code ownerId}.
 *
 * <p>Unlike {@code CourseEnrollment}, there is deliberately no uniqueness constraint tying a user to
 * a single active row per {@link #certificationId} - recertification is a normal event (a
 * credential lapses, the person re-earns it), and each award is its own historical record rather
 * than something that gets overwritten in place, the same "snapshot, don't mutate history"
 * reasoning {@link #expiresAt}'s own javadoc gives for not recomputing off a Certification that
 * might change later.
 */
@Entity
@Table(name = "user_certifications")
@Getter
@Setter
@NoArgsConstructor
public class UserCertification extends BaseEntity {

    public enum Status {
        ACTIVE, EXPIRED, REVOKED
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "certification_id", nullable = false)
    private UUID certificationId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "credential_number", length = 100)
    private String credentialNumber;

    @Column(name = "earned_at", nullable = false)
    private LocalDate earnedAt;

    /** Derived once at award time - see {@link Certification#getValidityMonths()}'s javadoc and {@code UserCertificationService#computeExpiresAt}. Null means this specific award never expires. */
    @Column(name = "expires_at")
    private LocalDate expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(length = 1000)
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public UserCertification(UUID organizationId, UUID certificationId, UUID userId, LocalDate earnedAt) {
        this.organizationId = organizationId;
        this.certificationId = certificationId;
        this.userId = userId;
        this.earnedAt = earnedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** Computed on read, not stored as a separate flag - {@link #status} tracks REVOKED (an explicit admin action) independently of whether {@link #expiresAt} has simply passed. */
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDate.now());
    }
}
