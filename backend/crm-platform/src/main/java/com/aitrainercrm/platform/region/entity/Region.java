package com.aitrainercrm.platform.region.entity;

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
 * One node in a nested org-chart tree ("North America" contains "US-West"/"US-East") sitting above
 * {@link com.aitrainercrm.platform.territory.entity.TerritoryRule} - a genuinely different concept,
 * not a duplicate of it. See V28's migration comment for the full "different question" reasoning
 * and for why {@link #parentRegionId} is a plain {@code UUID} field rather than a JPA {@code
 * @ManyToOne} relationship or even a real self-referencing database foreign key - {@code
 * RegionService} already has to walk the parent chain in application code to reject cycles (a
 * constraint no FK can express), so nothing here needs relationship mapping machinery on top of
 * that.
 *
 * <p>A {@link com.aitrainercrm.platform.organization.entity.Team} optionally points back at a
 * Region via {@code Team#regionId} - that's the only link between this tree and any actual CRM
 * data. {@code RegionService#rollup} walks from a Region down through every descendant, collects
 * every Team pointing at any of them, then every currently-active user on those teams, to arrive at
 * the set of owners whose Opportunities count toward that Region's numbers.
 */
@Entity
@Table(name = "regions")
@Getter
@Setter
@NoArgsConstructor
public class Region extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "parent_region_id")
    private UUID parentRegionId;

    @Column(length = 2000)
    private String description;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Region(UUID organizationId, String name, UUID parentRegionId) {
        this.organizationId = organizationId;
        this.name = name;
        this.parentRegionId = parentRegionId;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isRoot() {
        return parentRegionId == null;
    }
}
