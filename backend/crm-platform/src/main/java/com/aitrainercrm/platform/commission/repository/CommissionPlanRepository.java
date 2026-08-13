package com.aitrainercrm.platform.commission.repository;

import com.aitrainercrm.platform.commission.entity.CommissionPlan;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommissionPlanRepository extends JpaRepository<CommissionPlan, UUID> {

    Optional<CommissionPlan> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Page<CommissionPlan> findByOrganizationIdOrderByNameAsc(UUID organizationId, Pageable pageable);

    /** CommissionEngine's first lookup - an individual plan beats a team plan whenever both could
     * apply. Ordered by name so that if an org somehow has more than one active individual plan
     * for the same rep (nothing stops that at the schema level), which one wins is at least
     * deterministic rather than depending on undefined query order. */
    List<CommissionPlan> findByOrganizationIdAndOwnerUserIdAndActiveTrueOrderByNameAsc(UUID organizationId, UUID ownerUserId);

    List<CommissionPlan> findByOrganizationIdAndTeamIdAndActiveTrueOrderByNameAsc(UUID organizationId, UUID teamId);
}
