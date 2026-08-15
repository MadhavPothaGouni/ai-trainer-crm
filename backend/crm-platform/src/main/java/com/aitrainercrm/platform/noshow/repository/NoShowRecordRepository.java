package com.aitrainercrm.platform.noshow.repository;

import com.aitrainercrm.platform.noshow.entity.NoShowRecord;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoShowRecordRepository extends JpaRepository<NoShowRecord, UUID> {

    @Query("select n from NoShowRecord n where n.id = :id and n.organizationId = :organizationId and n.deletedAt is null")
    Optional<NoShowRecord> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<NoShowRecord> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    Page<NoShowRecord> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(UUID organizationId, Set<UUID> ownerIds, Pageable pageable);
}
