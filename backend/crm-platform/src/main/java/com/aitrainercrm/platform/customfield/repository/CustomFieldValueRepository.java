package com.aitrainercrm.platform.customfield.repository;

import com.aitrainercrm.platform.customfield.entity.CustomFieldValue;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomFieldValueRepository extends JpaRepository<CustomFieldValue, UUID> {

    List<CustomFieldValue> findByOrganizationIdAndRecordId(UUID organizationId, UUID recordId);

    Optional<CustomFieldValue> findByCustomFieldIdAndRecordId(UUID customFieldId, UUID recordId);

    void deleteByCustomFieldIdAndRecordId(UUID customFieldId, UUID recordId);

    void deleteByRecordId(UUID recordId);
}
