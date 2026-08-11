package com.aitrainercrm.platform.customfield.repository;

import com.aitrainercrm.platform.customfield.entity.CustomField;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomFieldRepository extends JpaRepository<CustomField, UUID> {

    Optional<CustomField> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<CustomField> findByOrganizationIdAndStandardEntityTypeOrderByDisplayOrderAsc(
            UUID organizationId, CustomField.StandardEntityType standardEntityType);

    List<CustomField> findByOrganizationIdAndCustomObjectIdOrderByDisplayOrderAsc(UUID organizationId, UUID customObjectId);

    List<CustomField> findByOrganizationIdOrderByDisplayOrderAsc(UUID organizationId);

    boolean existsByOrganizationIdAndStandardEntityTypeAndApiName(
            UUID organizationId, CustomField.StandardEntityType standardEntityType, String apiName);

    boolean existsByOrganizationIdAndCustomObjectIdAndApiName(UUID organizationId, UUID customObjectId, String apiName);

    long countByCustomObjectId(UUID customObjectId);
}
