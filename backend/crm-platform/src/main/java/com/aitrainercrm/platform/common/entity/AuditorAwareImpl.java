package com.aitrainercrm.platform.common.entity;

import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Feeds {@code @CreatedBy}/{@code @LastModifiedBy} on {@link BaseEntity}
 * from whoever is authenticated on the current request. Falls back to
 * empty (not a placeholder "system" UUID) when there's no authenticated
 * principal - migrations, scheduled jobs, and tests all run without one,
 * and a fabricated id there would be worse than a null createdBy.
 */
@Component
public class AuditorAwareImpl implements AuditorAware<UUID> {

    @Override
    public Optional<UUID> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal userPrincipal) {
            return Optional.of(userPrincipal.getId());
        }
        return Optional.empty();
    }
}
