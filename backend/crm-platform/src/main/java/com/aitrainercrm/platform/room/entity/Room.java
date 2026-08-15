package com.aitrainercrm.platform.room.entity;

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
 * A physical bookable space at the facility - see V53's migration comment for the gap this
 * fills. Shared-organization-catalog shape like {@link com.aitrainercrm.platform.locker.entity.Locker}:
 * no {@code ownerId}, TEAM/DEPARTMENT/ORGANIZATION scopes only.
 */
@Entity
@Table(name = "rooms")
@Getter
@Setter
@NoArgsConstructor
public class Room extends BaseEntity {

    public enum Status {
        ACTIVE, OUT_OF_SERVICE
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 50)
    private String label;

    @Column(length = 200)
    private String location;

    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(length = 2000)
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Room(UUID organizationId, String label) {
        this.organizationId = organizationId;
        this.label = label;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
