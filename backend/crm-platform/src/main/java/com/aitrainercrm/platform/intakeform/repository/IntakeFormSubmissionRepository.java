package com.aitrainercrm.platform.intakeform.repository;

import com.aitrainercrm.platform.intakeform.entity.IntakeFormSubmission;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IntakeFormSubmissionRepository extends JpaRepository<IntakeFormSubmission, UUID> {

    @Query("select s from IntakeFormSubmission s where s.id = :id and s.organizationId = :organizationId and s.deletedAt is null")
    Optional<IntakeFormSubmission> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<IntakeFormSubmission> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    Page<IntakeFormSubmission> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(UUID organizationId, Set<UUID> ownerIds, Pageable pageable);
}
