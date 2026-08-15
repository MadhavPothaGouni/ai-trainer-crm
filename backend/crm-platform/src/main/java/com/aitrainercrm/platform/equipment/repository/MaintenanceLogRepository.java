package com.aitrainercrm.platform.equipment.repository;

import com.aitrainercrm.platform.equipment.entity.MaintenanceLog;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MaintenanceLogRepository extends JpaRepository<MaintenanceLog, UUID> {

    @Query("select m from MaintenanceLog m where m.id = :id and m.organizationId = :organizationId and m.deletedAt is null")
    Optional<MaintenanceLog> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<MaintenanceLog> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    Page<MaintenanceLog> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(UUID organizationId, Set<UUID> ownerIds, Pageable pageable);
}
