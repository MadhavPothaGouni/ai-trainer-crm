package com.aitrainercrm.platform.role.service;

import com.aitrainercrm.platform.common.exception.DuplicateResourceException;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.role.entity.Role;
import com.aitrainercrm.platform.role.repository.PermissionRepository;
import com.aitrainercrm.platform.role.repository.RoleRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
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

        // systemRole=true on all three: RoleService/RoleController never let a tenant admin
        // rename, edit the permission set of, or delete OWNER/ADMIN/MEMBER - see
        // RoleService#assertMutable. Only custom roles created via POST /api/v1/roles are
        // system-role=false and therefore editable/deletable.
        Role owner = new Role(OWNER, "Full access to everything in this organization", organizationId, true);
        allPermissions.forEach(owner::addPermission);
        roleRepository.save(owner);

        Role admin = new Role(ADMIN, "Manage users, roles, and all business data", organizationId, true);
        allPermissions.stream()
                .filter(p -> !(p.getResource() == Permission.Resource.ORGANIZATION && p.getAction() == Permission.Action.DELETE))
                .forEach(admin::addPermission);
        roleRepository.save(admin);

        Role member = new Role(MEMBER, "Standard team member access", organizationId, true);
        // APPROVE was added here for APPROVAL_REQUEST (V19) specifically - being named as an
        // approver on someone else's request isn't an elevated-role concept the way ORDER/
        // INVOICE:APPROVE is (confirming an order, issuing an invoice); it's "were you
        // personally named," which ApprovalService checks directly regardless of scope (see
        // its javadoc). Safe to add here without also granting ORDER/INVOICE:APPROVE to every
        // MEMBER, since those two resources aren't in isCoreCrmResource below and never reach
        // this filter at all - the two @PreAuthorize gates on OrderController/InvoiceController
        // are the only thing standing between a MEMBER and confirming an order either way.
        Set<Permission.Action> memberActions = Set.of(
                Permission.Action.CREATE, Permission.Action.READ, Permission.Action.UPDATE, Permission.Action.APPROVE);
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
            case LEAD, CONTACT, ACCOUNT, OPPORTUNITY, ACTIVITY, QUOTE, TICKET, EMAIL_MESSAGE, CALENDAR_EVENT, ATTACHMENT, APPROVAL_REQUEST,
                    COURSE_ENROLLMENT, USER_CERTIFICATION, SEQUENCE_ENROLLMENT, BOOKING_LINK, CONTRACT, CLIENT_GOAL, TRAINING_SESSION,
                    NUTRITION_PLAN, BODY_MEASUREMENT, MEMBERSHIP, CLASS_SESSION, CLASS_ATTENDANCE, MAINTENANCE_LOG,
                    SHIFT, REFERRAL, PURCHASE_ORDER, CLIENT_DOCUMENT -> true;
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

    /**
     * Every role - system defaults plus custom - visible to one organization. Uses the
     * permissions-join-fetched query, not the plain derived one: callers of this method
     * (RoleController#list) serialize the result straight to RoleDto, which walks
     * role.getPermissions() - a lazy collection that would throw
     * LazyInitializationException once the request leaves this method, since
     * spring.jpa.open-in-view is false.
     */
    @Transactional(readOnly = true)
    public List<Role> listForOrganization(UUID organizationId) {
        return roleRepository.findByOrganizationIdWithPermissions(organizationId);
    }

    /**
     * Scoped lookup: a role from a *different* organization is treated as not found, not
     * forbidden - it should never be revealed to exist. Also permissions-join-fetched, for
     * the same reason as {@link #listForOrganization}.
     */
    @Transactional(readOnly = true)
    public Role getForOrganization(UUID organizationId, UUID roleId) {
        Role role = roleRepository.findByIdWithPermissions(roleId).orElseThrow(() -> new ResourceNotFoundException("Role", roleId));
        if (!organizationId.equals(role.getOrganizationId())) {
            throw new ResourceNotFoundException("Role", roleId);
        }
        return role;
    }

    @Transactional
    public Role createCustomRole(UUID organizationId, String name, String description, Set<UUID> permissionIds) {
        roleRepository.findByNameAndOrganizationId(name, organizationId).ifPresent(existing -> {
            throw new DuplicateResourceException("A role named '%s' already exists in this organization".formatted(name));
        });

        Role role = new Role(name, description, organizationId, false);
        resolvePermissions(permissionIds).forEach(role::addPermission);
        roleRepository.save(role);
        return role;
    }

    /** Evicts RolePermissionCacheService's per-role-id cache entry - the permission set below is what that cache holds. */
    @CacheEvict(value = "rolePermissions", key = "#roleId")
    @Transactional
    public Role updateCustomRole(UUID organizationId, UUID roleId, String name, String description, Set<UUID> permissionIds) {
        Role role = getForOrganization(organizationId, roleId);
        assertMutable(role);

        roleRepository.findByNameAndOrganizationId(name, organizationId)
                .filter(existing -> !existing.getId().equals(roleId))
                .ifPresent(existing -> {
                    throw new DuplicateResourceException("A role named '%s' already exists in this organization".formatted(name));
                });

        role.setName(name);
        role.setDescription(description);
        role.getPermissions().clear();
        resolvePermissions(permissionIds).forEach(role::addPermission);
        roleRepository.save(role);
        return role;
    }

    /** Evicts RolePermissionCacheService's cache entry for this role id - harmless no-op if nothing was cached. */
    @CacheEvict(value = "rolePermissions", key = "#roleId")
    @Transactional
    public void deleteCustomRole(UUID organizationId, UUID roleId) {
        Role role = getForOrganization(organizationId, roleId);
        assertMutable(role);
        // Users still holding this role simply lose the authorities it granted - see
        // user_roles' ON DELETE CASCADE in V1__init_schema.sql - rather than blocking the
        // delete on "is anyone assigned to this," which would make cleaning up an unused
        // custom role needlessly two-step (unassign everyone, then delete).
        roleRepository.delete(role);
    }

    /** OWNER/ADMIN/MEMBER ship with every organization and are never editable or deletable through the API - see the systemRole=true comment in createDefaultRolesForOrganization. */
    private void assertMutable(Role role) {
        if (role.isSystemRole()) {
            throw new ForbiddenException("System roles (%s) cannot be modified or deleted".formatted(role.getName()));
        }
    }

    /**
     * Resolves and validates a set of role ids for use in a user-role assignment. Unlike
     * {@link #resolvePermissions}, this also checks tenant ownership - a role id from another
     * organization must fail exactly like one that doesn't exist at all, never leaking that a
     * role with that id exists somewhere else.
     */
    public Set<Role> resolveForOrganization(UUID organizationId, Set<UUID> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Set.of();
        }
        Set<Role> roles = new HashSet<>(roleRepository.findAllById(roleIds));
        boolean allExistAndBelongToOrg =
                roles.size() == roleIds.size() && roles.stream().allMatch(r -> organizationId.equals(r.getOrganizationId()));
        if (!allExistAndBelongToOrg) {
            throw new ResourceNotFoundException("One or more role ids do not exist in this organization");
        }
        return roles;
    }

    private Set<Permission> resolvePermissions(Set<UUID> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return Set.of();
        }
        Set<Permission> permissions = new HashSet<>(permissionRepository.findAllById(permissionIds));
        if (permissions.size() != permissionIds.size()) {
            throw new ResourceNotFoundException("One or more permission ids do not exist");
        }
        return permissions;
    }
}
