package com.aitrainercrm.platform.compensation.repository;

import com.aitrainercrm.platform.compensation.entity.CompensationRecord;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompensationRecordRepository extends JpaRepository<CompensationRecord, UUID> {

    @Query("select c from CompensationRecord c where c.id = :id and c.organizationId = :organizationId and c.deletedAt is null")
    Optional<CompensationRecord> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<CompensationRecord> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    Page<CompensationRecord> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(UUID organizationId, Set<UUID> ownerIds, Pageable pageable);
}
