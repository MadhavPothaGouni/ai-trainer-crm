package com.aitrainercrm.platform.groupclass.entity;

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
 * A class *type* in the organization's catalog ("Spin 45", "Sunrise Yoga") - see V43's migration
 * comment for the three-level catalog/occurrence/roster shape this module introduces. Same
 * shared-organization-catalog pattern as {@link com.aitrainercrm.platform.product.entity.Product}/
 * {@link com.aitrainercrm.platform.membership.entity.MembershipPlan}: no {@code ownerId},
 * TEAM/DEPARTMENT/ORGANIZATION scopes only. {@link #defaultInstructorId} is only a convenience
 * default copied onto new {@link ClassSession}s - it is not itself an authorization boundary.
 */
@Entity
@Table(name = "group_classes")
@Getter
@Setter
@NoArgsConstructor
public class GroupClass extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(name = "default_instructor_id")
    private UUID defaultInstructorId;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes = 60;

    /** Null means unlimited attendance; a positive number is the max roster size a session of this class type allows unless overridden per-session. */
    @Column
    private Integer capacity;

    @Column(length = 200)
    private String location;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public GroupClass(UUID organizationId, String name) {
        this.organizationId = organizationId;
        this.name = name;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
