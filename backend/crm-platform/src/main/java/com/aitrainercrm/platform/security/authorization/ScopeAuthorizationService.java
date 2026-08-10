package com.aitrainercrm.platform.security.authorization;

import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.organization.entity.Team;
import com.aitrainercrm.platform.organization.repository.TeamRepository;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.entity.User;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Turns a static {@code @PreAuthorize("hasAnyAuthority(...)")} gate (which
 * only proves the caller holds *some* level of a permission) into the
 * record-level decision every "own"/"team"/"department"-scoped CRM
 * permission actually requires: given the record's owner, can *this* caller
 * act on it? {@code @PreAuthorize} can't answer that by itself - it has no
 * idea who owns the row being fetched - so account/contact/lead/opportunity
 * services all call in here after loading a record instead.
 *
 * <p>Scope precedence is OWN &lt; TEAM &lt; DEPARTMENT &lt; ORGANIZATION -
 * {@link #highestGranted} returns the broadest one the caller holds for a
 * given (resource, action), since a role can (and the seeded MEMBER role
 * does - see RoleService#createDefaultRolesForOrganization) hold more than
 * one scope for the same permission at once.
 *
 * <p><b>TEAM/DEPARTMENT scope is real but currently unreachable in
 * practice:</b> there's no Team-management API yet and nothing ever sets
 * {@link User#getTeamId()}, so every user's team is null today. That's not
 * a bug in this class - a null team correctly and safely resolves to "only
 * visible to yourself," the same as OWN scope - it just means the
 * TEAM/DEPARTMENT branches below are exercised by unit tests today rather
 * than real traffic, ready for whenever team assignment ships.
 */
@Service
@RequiredArgsConstructor
public class ScopeAuthorizationService {

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;

    public enum Access { NONE, OWN, TEAM, DEPARTMENT, ORGANIZATION }

    /** The broadest scope the caller holds for (resource, action), or NONE if they hold none of the four at all. */
    public Access highestGranted(UserPrincipal principal, Permission.Resource resource, Permission.Action action) {
        if (hasAuthority(principal, resource, action, Permission.Scope.ORGANIZATION)) return Access.ORGANIZATION;
        if (hasAuthority(principal, resource, action, Permission.Scope.DEPARTMENT)) return Access.DEPARTMENT;
        if (hasAuthority(principal, resource, action, Permission.Scope.TEAM)) return Access.TEAM;
        if (hasAuthority(principal, resource, action, Permission.Scope.OWN)) return Access.OWN;
        return Access.NONE;
    }

    /**
     * Guards a single-record operation (get/update/delete/assign on one
     * account/contact/lead/opportunity). Throws {@link ForbiddenException}
     * if the caller's highest granted scope doesn't reach this particular
     * record's owner; returns normally if it does.
     */
    public void assertCanAccess(UserPrincipal principal, Permission.Resource resource, Permission.Action action, UUID recordOwnerId) {
        Access access = highestGranted(principal, resource, action);
        boolean allowed = switch (access) {
            case NONE -> false;
            case ORGANIZATION -> true;
            case OWN -> principal.getId().equals(recordOwnerId);
            case TEAM -> shareATeam(principal.getId(), recordOwnerId, principal.getOrganizationId());
            case DEPARTMENT -> shareADepartment(principal.getId(), recordOwnerId, principal.getOrganizationId());
        };

        if (!allowed) {
            throw new ForbiddenException(
                    "You don't have permission to %s this %s".formatted(
                            action.name().toLowerCase(Locale.ROOT), resource.name().toLowerCase(Locale.ROOT)));
        }
    }

    /**
     * For list endpoints. Empty {@code Optional} means "no filter needed - the
     * caller can see the whole organization." A present value is the exact
     * set of owner ids they're allowed to see; callers pass that straight
     * into a {@code findByOrganizationIdAndOwnerIdIn(...)} query.
     *
     * <p>Throws {@link ForbiddenException} if the caller holds none of the
     * four scopes at all - callers should normally never reach that case,
     * since the controller's {@code @PreAuthorize} already requires at
     * least one, but this stays defensive rather than silently returning
     * an empty (i.e. "see nothing") result set for a request that should
     * never have been authorized in the first place.
     */
    public Optional<Set<UUID>> visibleOwnerIds(UserPrincipal principal, Permission.Resource resource, Permission.Action action) {
        Access access = highestGranted(principal, resource, action);
        return switch (access) {
            case NONE -> throw new ForbiddenException(
                    "You don't have permission to %s %ss".formatted(action.name().toLowerCase(Locale.ROOT), resource.name().toLowerCase(Locale.ROOT)));
            case ORGANIZATION -> Optional.empty();
            case OWN -> Optional.of(Set.of(principal.getId()));
            case TEAM -> Optional.of(teamMemberIds(principal));
            case DEPARTMENT -> Optional.of(departmentMemberIds(principal));
        };
    }

    private boolean hasAuthority(UserPrincipal principal, Permission.Resource resource, Permission.Action action, Permission.Scope scope) {
        String authority = "%s:%s:%s".formatted(resource, action, scope);
        return principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(authority));
    }

    private boolean shareATeam(UUID actorId, UUID ownerId, UUID organizationId) {
        if (actorId.equals(ownerId)) return true; // your own records are always visible to you, team or not
        UUID actorTeamId = teamIdOf(actorId, organizationId);
        UUID ownerTeamId = teamIdOf(ownerId, organizationId);
        return actorTeamId != null && actorTeamId.equals(ownerTeamId);
    }

    private boolean shareADepartment(UUID actorId, UUID ownerId, UUID organizationId) {
        if (actorId.equals(ownerId)) return true;
        String actorDepartment = departmentOf(actorId, organizationId);
        String ownerDepartment = departmentOf(ownerId, organizationId);
        return actorDepartment != null && actorDepartment.equals(ownerDepartment);
    }

    private Set<UUID> teamMemberIds(UserPrincipal principal) {
        UUID teamId = teamIdOf(principal.getId(), principal.getOrganizationId());
        if (teamId == null) {
            // Not on a team - TEAM scope degrades to exactly what OWN scope would show.
            return Set.of(principal.getId());
        }
        return new HashSet<>(userRepository.findIdsByOrganizationIdAndTeamId(principal.getOrganizationId(), teamId));
    }

    private Set<UUID> departmentMemberIds(UserPrincipal principal) {
        UUID teamId = teamIdOf(principal.getId(), principal.getOrganizationId());
        if (teamId == null) {
            return Set.of(principal.getId());
        }
        Team team = teamRepository.findByIdAndOrganizationId(teamId, principal.getOrganizationId()).orElse(null);
        if (team == null || team.getDepartment() == null) {
            // No department set on the caller's own team - can't widen beyond team visibility.
            return teamMemberIds(principal);
        }
        List<UUID> teamIdsInDepartment = teamRepository.findIdsByOrganizationIdAndDepartment(principal.getOrganizationId(), team.getDepartment());
        return new HashSet<>(userRepository.findIdsByOrganizationIdAndTeamIdIn(principal.getOrganizationId(), teamIdsInDepartment));
    }

    private UUID teamIdOf(UUID userId, UUID organizationId) {
        return userRepository.findActiveById(userId)
                .filter(u -> organizationId.equals(u.getOrganizationId()))
                .map(User::getTeamId)
                .orElse(null);
    }

    private String departmentOf(UUID userId, UUID organizationId) {
        UUID teamId = teamIdOf(userId, organizationId);
        if (teamId == null) return null;
        return teamRepository.findByIdAndOrganizationId(teamId, organizationId).map(Team::getDepartment).orElse(null);
    }
}
