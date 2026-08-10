package com.aitrainercrm.platform.user.repository;

import com.aitrainercrm.platform.user.entity.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    boolean existsByEmailAndDeletedAtIsNull(String email);

    @Query("select u from User u where u.id = :id and u.deletedAt is null")
    Optional<User> findActiveById(@Param("id") UUID id);

    long countByOrganizationIdAndDeletedAtIsNull(UUID organizationId);

    Page<User> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    /** Used to guard against ever leaving an organization with zero OWNERs - see UserService#assertOwnerRemains. */
    long countByOrganizationIdAndRoles_NameAndDeletedAtIsNull(UUID organizationId, String roleName);

    /** Every active user id on a given team - used by ScopeAuthorizationService to resolve TEAM-scope visibility for CRM records. */
    @Query("select u.id from User u where u.organizationId = :organizationId and u.teamId = :teamId and u.deletedAt is null")
    List<UUID> findIdsByOrganizationIdAndTeamId(@Param("organizationId") UUID organizationId, @Param("teamId") UUID teamId);

    /** Same as {@link #findIdsByOrganizationIdAndTeamId}, but across every team in a department - for DEPARTMENT-scope visibility. */
    @Query("select u.id from User u where u.organizationId = :organizationId and u.teamId in :teamIds and u.deletedAt is null")
    List<UUID> findIdsByOrganizationIdAndTeamIdIn(@Param("organizationId") UUID organizationId, @Param("teamIds") List<UUID> teamIds);
}
