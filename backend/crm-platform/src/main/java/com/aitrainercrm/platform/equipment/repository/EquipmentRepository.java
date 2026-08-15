package com.aitrainercrm.platform.equipment.repository;

import com.aitrainercrm.platform.equipment.entity.Equipment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EquipmentRepository extends JpaRepository<Equipment, UUID> {

    @Query("select e from Equipment e where e.id = :id and e.organizationId = :organizationId and e.deletedAt is null")
    Optional<Equipment> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<Equipment> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    boolean existsByIdAndOrganizationIdAndDeletedAtIsNull(UUID id, UUID organizationId);
}
