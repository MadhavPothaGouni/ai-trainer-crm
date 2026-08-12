package com.aitrainercrm.platform.salesgoals.repository;

import com.aitrainercrm.platform.salesgoals.entity.SalesGoal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesGoalRepository extends JpaRepository<SalesGoal, UUID> {

    Optional<SalesGoal> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Page<SalesGoal> findByOrganizationIdOrderByPeriodStartDesc(UUID organizationId, Pageable pageable);

    /** GET /sales-goals/mine's individual half - every goal assigned directly to this user, regardless of period. */
    List<SalesGoal> findByOrganizationIdAndOwnerUserId(UUID organizationId, UUID ownerUserId);

    /** GET /sales-goals/mine's team half - every goal assigned to the caller's current team. Skipped entirely when the caller has no team (teamId is null on User). */
    List<SalesGoal> findByOrganizationIdAndTeamId(UUID organizationId, UUID teamId);
}
