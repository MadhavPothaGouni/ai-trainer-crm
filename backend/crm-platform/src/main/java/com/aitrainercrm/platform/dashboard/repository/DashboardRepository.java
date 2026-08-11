package com.aitrainercrm.platform.dashboard.repository;

import com.aitrainercrm.platform.dashboard.entity.Dashboard;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DashboardRepository extends JpaRepository<Dashboard, UUID> {

    Optional<Dashboard> findByIdAndOrganizationIdAndDeletedAtIsNull(UUID id, UUID organizationId);

    Page<Dashboard> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    Page<Dashboard> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(UUID organizationId, Set<UUID> ownerIds, Pageable pageable);

    Optional<Dashboard> findByOrganizationIdAndOwnerIdAndDefaultDashboardTrueAndDeletedAtIsNull(UUID organizationId, UUID ownerId);
}
