package com.aitrainercrm.platform.importexport.repository;

import com.aitrainercrm.platform.importexport.entity.ImportJob;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportJobRepository extends JpaRepository<ImportJob, UUID> {

    Page<ImportJob> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    Optional<ImportJob> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
