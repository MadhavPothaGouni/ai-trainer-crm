package com.aitrainercrm.platform.organization.repository;

import com.aitrainercrm.platform.organization.entity.Organization;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    boolean existsBySlug(String slug);

    Optional<Organization> findBySlugAndDeletedAtIsNull(String slug);
}
