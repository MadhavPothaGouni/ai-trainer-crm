package com.aitrainercrm.platform.clientgoal.repository;

import com.aitrainercrm.platform.clientgoal.entity.ClientGoal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientGoalRepository extends JpaRepository<ClientGoal, UUID> {

    @Query("select g from ClientGoal g where g.id = :id and g.organizationId = :organizationId and g.deletedAt is null")
    Optional<ClientGoal> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<ClientGoal> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    Page<ClientGoal> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(UUID organizationId, Set<UUID> ownerIds, Pageable pageable);
}
