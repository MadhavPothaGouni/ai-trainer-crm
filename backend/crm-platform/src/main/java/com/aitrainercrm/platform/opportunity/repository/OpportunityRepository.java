package com.aitrainercrm.platform.opportunity.repository;

import com.aitrainercrm.platform.opportunity.entity.Opportunity;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OpportunityRepository extends JpaRepository<Opportunity, UUID> {

    @Query("select o from Opportunity o where o.id = :id and o.organizationId = :organizationId and o.deletedAt is null")
    Optional<Opportunity> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<Opportunity> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    Page<Opportunity> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(UUID organizationId, Set<UUID> ownerIds, Pageable pageable);

    /** Existence check that respects tenant + soft-delete - used by ActivityService to validate a related-to reference. */
    boolean existsByIdAndOrganizationIdAndDeletedAtIsNull(UUID id, UUID organizationId);
}
