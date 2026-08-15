package com.aitrainercrm.platform.equipment.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A physical asset the organization owns ("Treadmill #3", "Squat Rack B") - see V44's migration
 * comment for the gap this fills. Shared-organization-catalog shape like
 * {@link com.aitrainercrm.platform.product.entity.Product}/{@link com.aitrainercrm.platform.membership.entity.MembershipPlan}:
 * no {@code ownerId}, TEAM/DEPARTMENT/ORGANIZATION scopes only. {@link #status} is a free
 * (non-linear) state machine like tickets.status - equipment coming back from repair, or a
 * "retired" unit being reinstated, are both legitimate corrections, never blocked.
 */
@Entity
@Table(name = "equipment")
@Getter
@Setter
@NoArgsConstructor
public class Equipment extends BaseEntity {

    public enum Status {
        ACTIVE, OUT_OF_SERVICE, RETIRED
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 100)
    private String category;

    @Column(name = "serial_number", length = 100)
    private String serialNumber;

    @Column(length = 200)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "purchase_price", precision = 14, scale = 2)
    private BigDecimal purchasePrice;

    @Column(length = 2000)
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Equipment(UUID organizationId, String name) {
        this.organizationId = organizationId;
        this.name = name;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
