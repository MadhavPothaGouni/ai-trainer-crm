package com.aitrainercrm.platform.bodymeasurement.repository;

import com.aitrainercrm.platform.bodymeasurement.entity.BodyMeasurement;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BodyMeasurementRepository extends JpaRepository<BodyMeasurement, UUID> {

    @Query("select m from BodyMeasurement m where m.id = :id and m.organizationId = :organizationId and m.deletedAt is null")
    Optional<BodyMeasurement> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<BodyMeasurement> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    Page<BodyMeasurement> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(UUID organizationId, Set<UUID> ownerIds, Pageable pageable);
}
