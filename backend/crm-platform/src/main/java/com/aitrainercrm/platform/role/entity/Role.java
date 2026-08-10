package com.aitrainercrm.platform.role.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A named bundle of {@link Permission}s, scoped to one organization (system
 * roles like OWNER/ADMIN aside - see {@link #organizationId}). Assigning a
 * role to a user grants them every permission in the bundle; this is the
 * whole of this platform's RBAC model, deliberately - no per-user permission
 * overrides, because "why does this one user have an extra permission
 * nobody remembers granting" is exactly the kind of drift RBAC exists to
 * prevent.
 */
@Entity
@Table(name = "roles", uniqueConstraints = @UniqueConstraint(columnNames = {"organization_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
public class Role extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    /** Null for the platform-level system roles (OWNER/ADMIN/MEMBER); set for a tenant's custom roles. */
    @Column(name = "organization_id")
    private UUID organizationId;

    /** System roles ship with the platform and can't be edited/deleted by a tenant admin. */
    @Column(name = "is_system_role", nullable = false)
    private boolean systemRole = false;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<Permission> permissions = new HashSet<>();

    public Role(String name, String description, UUID organizationId, boolean systemRole) {
        this.name = name;
        this.description = description;
        this.organizationId = organizationId;
        this.systemRole = systemRole;
    }

    public void addPermission(Permission permission) {
        this.permissions.add(permission);
    }

    public void removePermission(Permission permission) {
        this.permissions.remove(permission);
    }
}
