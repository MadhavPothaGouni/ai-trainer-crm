package com.aitrainercrm.platform.emailtemplate.repository;

import com.aitrainercrm.platform.emailtemplate.entity.EmailTemplate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, UUID> {

    @Query("select t from EmailTemplate t where t.id = :id and t.organizationId = :organizationId and t.deletedAt is null")
    Optional<EmailTemplate> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<EmailTemplate> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    Page<EmailTemplate> findByOrganizationIdAndCategoryAndDeletedAtIsNull(
            UUID organizationId, EmailTemplate.Category category, Pageable pageable);
}
