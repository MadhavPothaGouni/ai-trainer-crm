package com.aitrainercrm.platform.security.userdetails;

import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.user.entity.User;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Spring Security's view of an authenticated user. Authorities are every
 * permission ("LEAD:CREATE:TEAM") from every role the user holds, flattened
 * into one set - {@code @PreAuthorize("hasAuthority('LEAD:CREATE:TEAM')")}
 * on a controller method is how permission checks actually get enforced
 * (see role/PermissionEvaluator for the scope-aware version).
 */
@Getter
public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final String email;
    private final String passwordHash;
    private final String fullName;
    private final UUID organizationId;
    private final boolean enabled;
    private final boolean accountNonLocked;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.fullName = user.getFullName();
        this.organizationId = user.getOrganizationId();
        this.enabled = user.getStatus() == User.Status.ACTIVE && !user.isDeleted();
        this.accountNonLocked = !user.isAccountLocked();
        this.authorities = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::toAuthorityName)
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .map(a -> (GrantedAuthority) a)
                .toList();
    }

    /** Used by JwtTokenProvider to rebuild a principal from a decoded token, without a role/permission fetch on every request. */
    public UserPrincipal(UUID id, String email, UUID organizationId, List<String> authorityNames) {
        this.id = id;
        this.email = email;
        this.passwordHash = null;
        this.fullName = null;
        this.organizationId = organizationId;
        this.enabled = true;
        this.accountNonLocked = true;
        this.authorities = authorityNames.stream().map(SimpleGrantedAuthority::new).toList();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
