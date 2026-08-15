package com.aitrainercrm.platform.timeoff.repository;

import com.aitrainercrm.platform.timeoff.entity.TimeOffRequest;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimeOffRequestRepository extends JpaRepository<TimeOffRequest, UUID> {

    @Query("select t from TimeOffRequest t where t.id = :id and t.organizationId = :organizationId and t.deletedAt is null")
    Optional<TimeOffRequest> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<TimeOffRequest> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    Page<TimeOffRequest> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(UUID organizationId, Set<UUID> ownerIds, Pageable pageable);
}
