package com.aitrainercrm.platform.region.repository;

import com.aitrainercrm.platform.region.entity.Region;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegionRepository extends JpaRepository<Region, UUID> {

    @Query("select r from Region r where r.id = :id and r.organizationId = :organizationId and r.deletedAt is null")
    Optional<Region> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    /** The whole org's active tree in one query, alphabetical - {@code RegionService} builds the
     * parent/child map for cycle detection and rollup traversal from this rather than issuing one
     * query per level, and the frontend uses the same shape to render the tree client-side. */
    List<Region> findByOrganizationIdAndDeletedAtIsNullOrderByNameAsc(UUID organizationId);

    boolean existsByOrganizationIdAndParentRegionIdAndDeletedAtIsNull(UUID organizationId, UUID parentRegionId);
}
