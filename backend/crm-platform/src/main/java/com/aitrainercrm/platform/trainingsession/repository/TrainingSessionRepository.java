package com.aitrainercrm.platform.trainingsession.repository;

import com.aitrainercrm.platform.trainingsession.entity.TrainingSession;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrainingSessionRepository extends JpaRepository<TrainingSession, UUID> {

    @Query("select s from TrainingSession s where s.id = :id and s.organizationId = :organizationId and s.deletedAt is null")
    Optional<TrainingSession> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<TrainingSession> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    Page<TrainingSession> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(UUID organizationId, Set<UUID> ownerIds, Pageable pageable);
}
