package com.aitrainercrm.platform.customfield.repository;

import com.aitrainercrm.platform.customfield.entity.CustomObject;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomObjectRepository extends JpaRepository<CustomObject, UUID> {

    Optional<CustomObject> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Page<CustomObject> findByOrganizationIdOrderByLabelAsc(UUID organizationId, Pageable pageable);

    List<CustomObject> findByOrganizationIdAndActiveTrueOrderByLabelAsc(UUID organizationId);

    boolean existsByOrganizationIdAndApiName(UUID organizationId, String apiName);
}
