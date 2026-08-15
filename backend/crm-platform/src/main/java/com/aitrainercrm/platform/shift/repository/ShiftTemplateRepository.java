package com.aitrainercrm.platform.shift.repository;

import com.aitrainercrm.platform.shift.entity.ShiftTemplate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShiftTemplateRepository extends JpaRepository<ShiftTemplate, UUID> {

    @Query("select s from ShiftTemplate s where s.id = :id and s.organizationId = :organizationId and s.deletedAt is null")
    Optional<ShiftTemplate> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<ShiftTemplate> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);
}
