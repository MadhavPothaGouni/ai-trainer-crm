package com.aitrainercrm.platform.intakeform.repository;

import com.aitrainercrm.platform.intakeform.entity.IntakeForm;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IntakeFormRepository extends JpaRepository<IntakeForm, UUID> {

    @Query("select f from IntakeForm f where f.id = :id and f.organizationId = :organizationId and f.deletedAt is null")
    Optional<IntakeForm> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<IntakeForm> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    boolean existsByIdAndOrganizationIdAndDeletedAtIsNull(UUID id, UUID organizationId);
}
