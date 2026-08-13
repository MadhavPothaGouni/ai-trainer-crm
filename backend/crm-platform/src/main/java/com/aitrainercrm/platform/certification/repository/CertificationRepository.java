package com.aitrainercrm.platform.certification.repository;

import com.aitrainercrm.platform.certification.entity.Certification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CertificationRepository extends JpaRepository<Certification, UUID> {

    @Query("select c from Certification c where c.id = :id and c.organizationId = :organizationId and c.deletedAt is null")
    Optional<Certification> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<Certification> findByOrganizationIdAndDeletedAtIsNullOrderByNameAsc(UUID organizationId, Pageable pageable);

    List<Certification> findByOrganizationIdAndActiveTrueAndDeletedAtIsNullOrderByNameAsc(UUID organizationId);
}
