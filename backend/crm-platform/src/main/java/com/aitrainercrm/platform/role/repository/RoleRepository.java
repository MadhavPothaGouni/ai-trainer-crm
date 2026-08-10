package com.aitrainercrm.platform.role.repository;

import com.aitrainercrm.platform.role.entity.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByNameAndOrganizationIdIsNull(String name);

    Optional<Role> findByNameAndOrganizationId(String name, UUID organizationId);

    List<Role> findByOrganizationId(UUID organizationId);

    List<Role> findByOrganizationIdIsNull();

    // Deliberately join-fetches permissions rather than relying on the entity's lazy
    // @ManyToMany: this app runs with spring.jpa.open-in-view=false, so a Role handed
    // back from a plain findByOrganizationId()/findById() is detached the moment the
    // repository call returns - touching role.getPermissions() afterward (e.g. while
    // building RoleDto in the controller) throws LazyInitializationException instead of
    // quietly re-querying. These two methods are for exactly the call sites that need
    // to serialize permissions back out, so the join happens up front in one query.
    @Query("select distinct r from Role r left join fetch r.permissions where r.organizationId = :organizationId")
    List<Role> findByOrganizationIdWithPermissions(@Param("organizationId") UUID organizationId);

    @Query("select r from Role r left join fetch r.permissions where r.id = :roleId")
    Optional<Role> findByIdWithPermissions(@Param("roleId") UUID roleId);
}
