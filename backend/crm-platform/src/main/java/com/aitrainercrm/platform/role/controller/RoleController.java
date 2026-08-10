package com.aitrainercrm.platform.role.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.role.dto.CreateRoleRequest;
import com.aitrainercrm.platform.role.dto.PermissionDto;
import com.aitrainercrm.platform.role.dto.RoleDto;
import com.aitrainercrm.platform.role.dto.UpdateRoleRequest;
import com.aitrainercrm.platform.role.entity.Role;
import com.aitrainercrm.platform.role.repository.PermissionRepository;
import com.aitrainercrm.platform.role.service.RoleService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Custom-role management, org-scoped throughout - every method reads the
 * organization id off the caller's own JWT ({@code principal.getOrganizationId()})
 * rather than trusting a path/query parameter, so there's no way to
 * enumerate or edit another tenant's roles by guessing a UUID. The three
 * defaults (OWNER/ADMIN/MEMBER) show up in the same list endpoint but
 * reject any write - see RoleService#assertMutable.
 */
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;
    private final PermissionRepository permissionRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE:READ:ORGANIZATION')")
    public ApiResponse<java.util.List<RoleDto>> list(@AuthenticationPrincipal UserPrincipal principal) {
        java.util.List<RoleDto> roles = roleService.listForOrganization(principal.getOrganizationId()).stream()
                .map(RoleDto::from)
                .toList();
        return ApiResponse.ok(roles);
    }

    @GetMapping("/{roleId}")
    @PreAuthorize("hasAuthority('ROLE:READ:ORGANIZATION')")
    public ApiResponse<RoleDto> get(@PathVariable UUID roleId, @AuthenticationPrincipal UserPrincipal principal) {
        Role role = roleService.getForOrganization(principal.getOrganizationId(), roleId);
        return ApiResponse.ok(RoleDto.from(role));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ROLE:CREATE:ORGANIZATION')")
    public ApiResponse<RoleDto> create(
            @Valid @RequestBody CreateRoleRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        Role role = roleService.createCustomRole(
                principal.getOrganizationId(), request.name(), request.description(), request.permissionIds());
        return ApiResponse.ok(RoleDto.from(role), "Role created");
    }

    @PutMapping("/{roleId}")
    @PreAuthorize("hasAuthority('ROLE:UPDATE:ORGANIZATION')")
    public ApiResponse<RoleDto> update(
            @PathVariable UUID roleId, @Valid @RequestBody UpdateRoleRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Role role = roleService.updateCustomRole(
                principal.getOrganizationId(), roleId, request.name(), request.description(), request.permissionIds());
        return ApiResponse.ok(RoleDto.from(role), "Role updated");
    }

    @DeleteMapping("/{roleId}")
    @PreAuthorize("hasAuthority('ROLE:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID roleId, @AuthenticationPrincipal UserPrincipal principal) {
        roleService.deleteCustomRole(principal.getOrganizationId(), roleId);
        return ApiResponse.ok(null, "Role deleted");
    }

    /**
     * The full permission catalog, unfiltered by organization - it's
     * platform data (see V2__seed_permission_catalog.sql), the same for
     * every tenant. Read access is gated on ROLE:READ since browsing the
     * catalog is only useful in service of building a role.
     */
    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('ROLE:READ:ORGANIZATION')")
    public ApiResponse<java.util.List<PermissionDto>> listPermissions() {
        java.util.List<PermissionDto> permissions =
                permissionRepository.findAll().stream().map(PermissionDto::from).toList();
        return ApiResponse.ok(permissions);
    }
}
