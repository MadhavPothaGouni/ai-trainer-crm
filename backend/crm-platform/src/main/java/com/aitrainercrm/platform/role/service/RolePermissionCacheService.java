package com.aitrainercrm.platform.role.service;

import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.role.repository.RoleRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves a role id to its flattened permission-authority-name list, cached (see
 * config: spring.cache.type - Redis in dev/prod, in-memory "simple" in tests).
 *
 * <p>This exists so JwtTokenProvider can put a handful of small role ids in the JWT
 * instead of the full permission list - see JwtTokenProvider's class javadoc. The
 * catalog has grown into the hundreds of permissions (every module adds its own
 * CRUD/scope combinations), so a role like OWNER that holds all of them would blow
 * past Tomcat's default max-http-header-size if that full list were embedded in
 * every request's Authorization header. Resolving from a cached lookup instead means
 * the token only ever needs to carry role ids, which don't grow with the catalog.
 *
 * <p>Cache entries are keyed by role id (not name+org), so a role rename never
 * invalidates them; {@link RoleService#updateCustomRole} and
 * {@link RoleService#deleteCustomRole} evict by id whenever a role's permission set
 * actually changes or the role goes away. A user whose role permissions changed
 * mid-session keeps their old authorities until their short-lived access token
 * naturally expires and they re-authenticate - the same staleness window that
 * already existed before this change, since the token was always a point-in-time
 * snapshot.
 */
@Service
@RequiredArgsConstructor
public class RolePermissionCacheService {

    private final RoleRepository roleRepository;

    @Cacheable(value = "rolePermissions", key = "#roleId")
    @Transactional(readOnly = true)
    public List<String> getAuthorityNames(UUID roleId) {
        return roleRepository
                .findByIdWithPermissions(roleId)
                .map(role -> role.getPermissions().stream().map(Permission::toAuthorityName).distinct().toList())
                .orElseGet(List::of);
    }
}
