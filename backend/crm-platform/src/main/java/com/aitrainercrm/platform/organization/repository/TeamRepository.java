package com.aitrainercrm.platform.organization.repository;

import com.aitrainercrm.platform.organization.entity.Team;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * There's no Team management API yet (no controller, no CRUD) - this exists
 * solely so {@link com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService}
 * can resolve DEPARTMENT-scope visibility (which team ids share a department)
 * for CRM records. Team assignment itself isn't wired into any endpoint
 * yet either (see User#teamId), so in practice every user's team is null
 * today and TEAM/DEPARTMENT-scope permissions fall back to OWN-equivalent
 * visibility - see ScopeAuthorizationService's javadoc.
 */
public interface TeamRepository extends JpaRepository<Team, UUID> {

    Optional<Team> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Query("select t.id from Team t where t.organizationId = :organizationId and t.department = :department")
    List<UUID> findIdsByOrganizationIdAndDepartment(@Param("organizationId") UUID organizationId, @Param("department") String department);
}
