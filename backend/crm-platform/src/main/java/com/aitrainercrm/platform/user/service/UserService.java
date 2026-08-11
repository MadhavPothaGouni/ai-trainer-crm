package com.aitrainercrm.platform.user.service;

import com.aitrainercrm.platform.audit.event.OrgManagementAuditEvents;
import com.aitrainercrm.platform.auth.repository.PasswordResetTokenRepository;
import com.aitrainercrm.platform.auth.repository.RefreshTokenRepository;
import com.aitrainercrm.platform.auth.entity.PasswordResetToken;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.DuplicateResourceException;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.config.SecurityProperties;
import com.aitrainercrm.platform.notification.email.EmailService;
import com.aitrainercrm.platform.organization.repository.TeamRepository;
import com.aitrainercrm.platform.role.entity.Role;
import com.aitrainercrm.platform.role.service.RoleService;
import com.aitrainercrm.platform.security.token.SecureTokenService;
import com.aitrainercrm.platform.user.dto.CreateUserRequest;
import com.aitrainercrm.platform.user.dto.UpdateProfileRequest;
import com.aitrainercrm.platform.user.entity.User;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Everything that manages a teammate's account within an organization:
 * listing/reading (always org-scoped - see {@link #assertSameOrganization}),
 * inviting, role assignment, activation status, and removal. Registration
 * itself (the very first user + organization) lives in AuthService, not
 * here - this service assumes the organization and its default roles
 * already exist.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleService roleService;
    private final TeamRepository teamRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final SecureTokenService secureTokenService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final SecurityProperties securityProperties;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<User> list(UUID organizationId, Pageable pageable) {
        return userRepository.findByOrganizationIdAndDeletedAtIsNull(organizationId, pageable);
    }

    @Transactional(readOnly = true)
    public User get(UUID organizationId, UUID userId) {
        User user = userRepository.findActiveById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
        assertSameOrganization(organizationId, user);
        return user;
    }

    @Transactional
    public User invite(UUID organizationId, User actor, CreateUserRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailAndDeletedAtIsNull(email)) {
            throw new DuplicateResourceException("An account with this email already exists");
        }

        Set<Role> roles = roleService.resolveForOrganization(organizationId, request.roleIds());
        if (roles.isEmpty()) {
            roles = Set.of(roleService.getMemberRole(organizationId));
        }

        // An unusable random password - the invitee authenticates for the first time via the
        // set-password link below, never by an admin choosing (and thus knowing) their password.
        User user = new User(email, passwordEncoder.encode(secureTokenService.generateRawToken()), request.firstName(), request.lastName());
        user.setOrganizationId(organizationId);
        roles.forEach(user::addRole);
        userRepository.save(user);

        String rawToken = secureTokenService.generateRawToken();
        Instant expiresAt = Instant.now().plus(securityProperties.passwordResetTokenExpirationMinutes(), ChronoUnit.MINUTES);
        passwordResetTokenRepository.save(new PasswordResetToken(user.getId(), secureTokenService.hash(rawToken), expiresAt));
        emailService.sendInvitationEmail(user.getEmail(), actor.getFullName(), rawToken);

        events.publishEvent(new OrgManagementAuditEvents.UserInvited(actor.getId(), user.getId(), user.getEmail(), organizationId));
        return user;
    }

    @Transactional
    public User updateOwnProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findActiveById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPhone(request.phone());
        if (request.timezone() != null) {
            user.setTimezone(request.timezone());
        }
        if (request.locale() != null) {
            user.setLocale(request.locale());
        }
        return userRepository.save(user);
    }

    @Transactional
    public User updateRoles(UUID organizationId, User actor, UUID targetUserId, Set<UUID> roleIds) {
        User target = get(organizationId, targetUserId);
        Set<Role> roles = roleService.resolveForOrganization(organizationId, roleIds);
        if (roles.isEmpty()) {
            throw new BusinessException("INVALID_ROLE_ASSIGNMENT", "At least one role is required", HttpStatus.BAD_REQUEST);
        }

        boolean hadOwner = hasRole(target, RoleService.OWNER);
        boolean willHaveOwner = roles.stream().anyMatch(r -> RoleService.OWNER.equals(r.getName()));
        if (hadOwner && !willHaveOwner) {
            assertAnotherOwnerRemains(organizationId, target.getId());
        }

        target.setRoles(new HashSet<>(roles));
        userRepository.save(target);
        events.publishEvent(new OrgManagementAuditEvents.UserRolesChanged(actor.getId(), target.getId(), organizationId));
        return target;
    }

    @Transactional
    public User updateStatus(UUID organizationId, User actor, UUID targetUserId, User.Status newStatus) {
        // Same reasoning as remove(): check the target id against the actor's own id before
        // fetching anything.
        if (targetUserId.equals(actor.getId()) && newStatus != User.Status.ACTIVE) {
            throw new ForbiddenException("You cannot change your own account status");
        }

        User target = get(organizationId, targetUserId);
        if (hasRole(target, RoleService.OWNER) && newStatus != User.Status.ACTIVE) {
            assertAnotherOwnerRemains(organizationId, target.getId());
        }

        target.setStatus(newStatus);
        userRepository.save(target);
        if (newStatus != User.Status.ACTIVE) {
            refreshTokenRepository.revokeAllForUser(target.getId(), Instant.now());
        }

        events.publishEvent(new OrgManagementAuditEvents.UserStatusChanged(actor.getId(), target.getId(), newStatus.name(), organizationId));
        return target;
    }

    /**
     * The other half of closing ScopeAuthorizationService's long-documented gap - TeamController
     * (organization/) covers Team CRUD, this is what actually puts a user on one. {@code teamId}
     * null is a legitimate value (unassign) - see UpdateUserTeamRequest's javadoc - so unlike
     * updateRoles/updateStatus there's no "reject null" case, just an existence check when it's
     * non-null.
     */
    @Transactional
    public User updateTeam(UUID organizationId, User actor, UUID targetUserId, UUID teamId) {
        User target = get(organizationId, targetUserId);
        if (teamId != null && teamRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(teamId, organizationId).isEmpty()) {
            throw new ResourceNotFoundException("Team", teamId);
        }

        target.setTeamId(teamId);
        userRepository.save(target);
        events.publishEvent(new OrgManagementAuditEvents.UserTeamChanged(actor.getId(), target.getId(), teamId, organizationId));
        return target;
    }

    @Transactional
    public void remove(UUID organizationId, User actor, UUID targetUserId) {
        // Checked against the id the caller asked to remove, before ever fetching anything -
        // "am I targeting myself" doesn't need a lookup, and this way the rule is enforced
        // even if the actor's own row is somehow missing/inconsistent.
        if (targetUserId.equals(actor.getId())) {
            throw new ForbiddenException("You cannot remove your own account through this endpoint");
        }

        User target = get(organizationId, targetUserId);
        if (hasRole(target, RoleService.OWNER)) {
            assertAnotherOwnerRemains(organizationId, target.getId());
        }

        target.setDeletedAt(Instant.now());
        target.setStatus(User.Status.DEACTIVATED);
        userRepository.save(target);
        refreshTokenRepository.revokeAllForUser(target.getId(), Instant.now());

        events.publishEvent(new OrgManagementAuditEvents.UserRemoved(actor.getId(), target.getId(), organizationId));
    }

    private boolean hasRole(User user, String roleName) {
        return user.getRoles().stream().anyMatch(role -> roleName.equals(role.getName()));
    }

    /** Refuses to complete an operation that would leave an organization with zero OWNERs - see the OWNER-count guard in updateRoles/updateStatus/remove. */
    private void assertAnotherOwnerRemains(UUID organizationId, UUID excludingUserId) {
        long ownerCount = userRepository.countByOrganizationIdAndRoles_NameAndDeletedAtIsNull(organizationId, RoleService.OWNER);
        // The target user is still counted as an OWNER at this point (the caller hasn't saved
        // the change yet), so "at least 2 owners today" is the right threshold for "at least 1
        // will remain after this one loses OWNER."
        if (ownerCount <= 1) {
            throw new ForbiddenException("An organization must always have at least one OWNER");
        }
    }

    private void assertSameOrganization(UUID organizationId, User user) {
        if (!organizationId.equals(user.getOrganizationId())) {
            throw new ResourceNotFoundException("User", user.getId());
        }
    }
}
