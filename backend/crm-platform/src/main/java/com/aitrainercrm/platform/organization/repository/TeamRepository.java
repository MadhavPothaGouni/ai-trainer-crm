package com.aitrainercrm.platform.organization.repository;

import com.aitrainercrm.platform.organization.entity.Team;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Originally existed solely so {@link
 * com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService}
 * had something to resolve TEAM/DEPARTMENT-scope visibility against - see
 * that class's javadoc and V16's migration comment for how "no Team
 * management API, nothing ever sets a team id" was found and closed.
 * {@code findByIdAndOrganizationIdAndDeletedAtIsNull}/
 * {@code findIdsByOrganizationIdAndDepartment} now filter out soft-deleted
 * teams (V16 added {@code deletedAt}) so a deleted team can't keep granting
 * DEPARTMENT-scope visibility through its former department string.
 */
public interface TeamRepository extends JpaRepository<Team, UUID> {

    Optional<Team> findByIdAndOrganizationIdAndDeletedAtIsNull(UUID id, UUID organizationId);

    Page<Team> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    List<Team> findByOrganizationIdAndDeletedAtIsNullOrderByNameAsc(UUID organizationId);

    @Query("select t.id from Team t where t.organizationId = :organizationId and t.department = :department and t.deletedAt is null")
    List<UUID> findIdsByOrganizationIdAndDepartment(@Param("organizationId") UUID organizationId, @Param("department") String department);
}
