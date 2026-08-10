package com.aitrainercrm.platform.role.repository;

import com.aitrainercrm.platform.role.entity.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByNameAndOrganizationIdIsNull(String name);

    Optional<Role> findByNameAndOrganizationId(String name, UUID organizationId);

    List<Role> findByOrganizationId(UUID organizationId);

    List<Role> findByOrganizationIdIsNull();
}
