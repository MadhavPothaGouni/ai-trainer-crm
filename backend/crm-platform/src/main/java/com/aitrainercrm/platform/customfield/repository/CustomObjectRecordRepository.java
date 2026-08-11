package com.aitrainercrm.platform.customfield.repository;

import com.aitrainercrm.platform.customfield.entity.CustomObjectRecord;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomObjectRecordRepository extends JpaRepository<CustomObjectRecord, UUID> {

    Optional<CustomObjectRecord> findByIdAndCustomObjectIdAndDeletedAtIsNull(UUID id, UUID customObjectId);

    Page<CustomObjectRecord> findByCustomObjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID customObjectId, Pageable pageable);

    long countByCustomObjectIdAndDeletedAtIsNull(UUID customObjectId);
}
