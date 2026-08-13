package com.aitrainercrm.platform.gdpr.repository;

import com.aitrainercrm.platform.gdpr.entity.DataSubjectRequest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataSubjectRequestRepository extends JpaRepository<DataSubjectRequest, UUID> {

    Page<DataSubjectRequest> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    Optional<DataSubjectRequest> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
