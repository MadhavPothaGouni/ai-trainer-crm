package com.aitrainercrm.platform.lead.repository;

import com.aitrainercrm.platform.lead.entity.Lead;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeadRepository extends JpaRepository<Lead, UUID> {

    @Query("select l from Lead l where l.id = :id and l.organizationId = :organizationId and l.deletedAt is null")
    Optional<Lead> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<Lead> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    Page<Lead> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(UUID organizationId, Set<UUID> ownerIds, Pageable pageable);

    /** Unpaginated variants for CSV export - see AccountRepository's identical pair for why. */
    List<Lead> findByOrganizationIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID organizationId);

    List<Lead> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNullOrderByCreatedAtDesc(UUID organizationId, Set<UUID> ownerIds);

    /** Existence check that respects tenant + soft-delete - used by ActivityService to validate a related-to reference. */
    boolean existsByIdAndOrganizationIdAndDeletedAtIsNull(UUID id, UUID organizationId);
}
