package com.aitrainercrm.platform.certification.entity;

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
 * A credential the organization recognizes (e.g. "Certified Solutions Consultant," issued by an
 * internal training team or a third-party body) - the admin-maintained catalog half of this pair,
 * same shape as {@link com.aitrainercrm.platform.course.entity.Course}: shared organization data, no
 * {@code ownerId}, TEAM/DEPARTMENT/ORGANIZATION scope only (see V31's migration comment).
 *
 * <p>{@link #validityMonths} is null for a credential that never expires; otherwise it's read once,
 * at award time, by {@code UserCertificationService#computeExpiresAt} to stamp a new {@link
 * UserCertification#getExpiresAt()} - changing it here later does not retroactively reshape
 * already-issued awards, see that method's javadoc.
 */
@Entity
@Table(name = "certifications")
@Getter
@Setter
@NoArgsConstructor
public class Certification extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "issuing_body", length = 200)
    private String issuingBody;

    @Column(length = 2000)
    private String description;

    @Column(name = "validity_months")
    private Integer validityMonths;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Certification(UUID organizationId, String name) {
        this.organizationId = organizationId;
        this.name = name;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
