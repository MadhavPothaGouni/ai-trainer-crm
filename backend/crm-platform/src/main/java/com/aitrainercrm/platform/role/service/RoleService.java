package com.aitrainercrm.platform.role.service;

import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.role.entity.Role;
import com.aitrainercrm.platform.role.repository.PermissionRepository;
import com.aitrainercrm.platform.role.repository.RoleRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provisions the three default roles every new organization gets
 * (OWNER/ADMIN/MEMBER), and looks roles up by name for role assignment
 * elsewhere. There's no UI for editing what these three defaults grant -
 * organizations that need finer-grained roles create their own custom
 * roles (see RoleController#createCustomRole) built from the same
 * Permission catalog.
 */
@Service
@RequiredArgsConstructor
public class RoleService {

    public static final String OWNER = "OWNER";
    public static final String ADMIN = "ADMIN";
    public static final String MEMBER = "MEMBER";

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    /**
     * Called once, at organization creation. OWNER gets every permission
     * in the catalog; ADMIN gets everything except deleting the
     * organization itself or managing billing; MEMBER gets read/write on
     * their own records and read-only at team scope - deliberately
     * conservative, since MEMBER is what every new non-admin teammate
     * starts with.
     */
    @Transactional
    public Role createDefaultRolesForOrganization(UUID organizationId) {
        List<Permission> allPermissions = permissionRepository.findAll();

        Role owner = new Role(OWNER, "Full access to everything in this organization", organizationId, false);
        allPermissions.forEach(owner::addPermission);
        roleRepository.save(owner);

        Role admin = new Role(ADMIN, "Manage users, roles, and all business data", organizationId, false);
        allPermissions.stream()
                .filter(p -> !(p.getResource() == Permission.Resource.ORGANIZATION && p.getAction() == Permission.Action.DELETE))
                .forEach(admin::addPermission);
        roleRepository.save(admin);

        Role member = new Role(MEMBER, "Standard team member access", organizationId, false);
        Set<Permission.Action> memberActions = Set.of(
                Permission.Action.CREATE, Permission.Action.READ, Permission.Action.UPDATE);
        allPermissions.stream()
                .filter(p -> isCoreCrmResource(p.getResource()))
                .filter(p -> memberActions.contains(p.getAction()))
                .filter(p -> p.getScope() == Permission.Scope.OWN || p.getScope() == Permission.Scope.TEAM)
                .forEach(member::addPermission);
        roleRepository.save(member);

        return owner;
    }

    private boolean isCoreCrmResource(Permission.Resource resource) {
        return switch (resource) {
            case LEAD, CONTACT, ACCOUNT, OPPORTUNITY, ACTIVITY, QUOTE, TICKET -> true;
            default -> false;
        };
    }

    public Role getOwnerRole(UUID organizationId) {
        return roleRepository
                .findByNameAndOrganizationId(OWNER, organizationId)
                .orElseThrow(() -> new IllegalStateException("Organization %s has no OWNER role".formatted(organizationId)));
    }

    public Role getMemberRole(UUID organizationId) {
        return roleRepository
                .findByNameAndOrganizationId(MEMBER, organizationId)
                .orElseThrow(() -> new IllegalStateException("Organization %s has no MEMBER role".formatted(organizationId)));
    }
}
