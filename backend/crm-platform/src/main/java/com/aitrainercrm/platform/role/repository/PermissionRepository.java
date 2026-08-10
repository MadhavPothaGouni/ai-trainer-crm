package com.aitrainercrm.platform.role.repository;

import com.aitrainercrm.platform.role.entity.Permission;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {
}
