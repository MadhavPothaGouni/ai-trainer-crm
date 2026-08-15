package com.aitrainercrm.platform.locker.entity;

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
 * A physical locker at the facility - see V50's migration comment for the gap this fills.
 * Shared-organization-catalog shape like {@link com.aitrainercrm.platform.vendor.entity.Vendor}:
 * no {@code ownerId}, TEAM/DEPARTMENT/ORGANIZATION scopes only.
 */
@Entity
@Table(name = "lockers")
@Getter
@Setter
@NoArgsConstructor
public class Locker extends BaseEntity {

    public enum Size {
        SMALL, MEDIUM, LARGE
    }

    public enum Status {
        ACTIVE, OUT_OF_SERVICE
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 50)
    private String label;

    @Column(length = 200)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Size size = Size.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(length = 2000)
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Locker(UUID organizationId, String label) {
        this.organizationId = organizationId;
        this.label = label;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
