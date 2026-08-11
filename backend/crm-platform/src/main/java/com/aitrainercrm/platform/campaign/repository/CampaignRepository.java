package com.aitrainercrm.platform.campaign.repository;

import com.aitrainercrm.platform.campaign.entity.Campaign;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampaignRepository extends JpaRepository<Campaign, UUID> {

    @Query("select c from Campaign c where c.id = :id and c.organizationId = :organizationId and c.deletedAt is null")
    Optional<Campaign> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<Campaign> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    /** Used by the CSV export endpoint - unpaginated, org-scoped. */
    java.util.List<Campaign> findByOrganizationIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID organizationId);
}
