package com.aitrainercrm.platform.equipment.repository;

import com.aitrainercrm.platform.equipment.entity.EquipmentReservation;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EquipmentReservationRepository extends JpaRepository<EquipmentReservation, UUID> {

    @Query("select r from EquipmentReservation r where r.id = :id and r.organizationId = :organizationId and r.deletedAt is null")
    Optional<EquipmentReservation> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<EquipmentReservation> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    Page<EquipmentReservation> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(UUID organizationId, Set<UUID> ownerIds, Pageable pageable);
}
