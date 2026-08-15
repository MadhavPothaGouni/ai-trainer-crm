package com.aitrainercrm.platform.locker.repository;

import com.aitrainercrm.platform.locker.entity.LockerAssignment;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LockerAssignmentRepository extends JpaRepository<LockerAssignment, UUID> {

    @Query("select a from LockerAssignment a where a.id = :id and a.organizationId = :organizationId and a.deletedAt is null")
    Optional<LockerAssignment> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<LockerAssignment> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    Page<LockerAssignment> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(UUID organizationId, Set<UUID> ownerIds, Pageable pageable);
}
