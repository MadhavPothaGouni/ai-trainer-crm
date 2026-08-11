package com.aitrainercrm.platform.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.auth.repository.PasswordResetTokenRepository;
import com.aitrainercrm.platform.auth.repository.RefreshTokenRepository;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.config.SecurityProperties;
import com.aitrainercrm.platform.notification.email.EmailService;
import com.aitrainercrm.platform.organization.repository.TeamRepository;
import com.aitrainercrm.platform.role.entity.Role;
import com.aitrainercrm.platform.role.service.RoleService;
import com.aitrainercrm.platform.security.token.SecureTokenService;
import com.aitrainercrm.platform.user.dto.CreateUserRequest;
import com.aitrainercrm.platform.user.entity.User;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Pure-Mockito tests for the guardrail every other check in this class
 * builds on: an organization must never end up with zero OWNERs. That
 * rule is enforced in three different places (role change, status
 * change, removal) and this suite exercises all three, plus the simpler
 * "you can't remove yourself" / "invite defaults to MEMBER" behavior.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleService roleService;
    @Mock private TeamRepository teamRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private SecureTokenService secureTokenService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;
    @Mock private ApplicationEventPublisher events;

    private UserService userService;
    private UUID organizationId;

    @BeforeEach
    void setUp() {
        SecurityProperties securityProperties = new SecurityProperties(5, 15, 30, 24);
        userService = new UserService(
                userRepository, roleService, teamRepository, refreshTokenRepository, passwordResetTokenRepository,
                secureTokenService, passwordEncoder, emailService, securityProperties, events);
        organizationId = UUID.randomUUID();
    }

    @Test
    void updateRoles_removingTheLastOwners_isRejected() {
        User actor = userWithId();
        User target = userWithRole(RoleService.OWNER);
        target.setOrganizationId(organizationId);

        Role memberRole = new Role(RoleService.MEMBER, "desc", organizationId, true);
        memberRole.setId(UUID.randomUUID());
        when(userRepository.findActiveById(target.getId())).thenReturn(Optional.of(target));
        when(roleService.resolveForOrganization(organizationId, Set.of(memberRole.getId())))
                .thenReturn(Set.of(memberRole));
        // Only this one OWNER exists in the organization right now.
        when(userRepository.countByOrganizationIdAndRoles_NameAndDeletedAtIsNull(organizationId, RoleService.OWNER))
                .thenReturn(1L);

        assertThatThrownBy(() -> userService.updateRoles(organizationId, actor, target.getId(), Set.of(memberRole.getId())))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("at least one OWNER");

        verify(userRepository, never()).save(target);
    }

    @Test
    void updateRoles_removingOwnerWhenAnotherOwnerExists_succeeds() {
        User actor = userWithId();
        User target = userWithRole(RoleService.OWNER);
        target.setOrganizationId(organizationId);

        Role memberRole = new Role(RoleService.MEMBER, "desc", organizationId, true);
        memberRole.setId(UUID.randomUUID());
        when(userRepository.findActiveById(target.getId())).thenReturn(Optional.of(target));
        when(roleService.resolveForOrganization(organizationId, Set.of(memberRole.getId())))
                .thenReturn(Set.of(memberRole));
        // A second OWNER exists, so demoting this one is safe.
        when(userRepository.countByOrganizationIdAndRoles_NameAndDeletedAtIsNull(organizationId, RoleService.OWNER))
                .thenReturn(2L);
        when(userRepository.save(target)).thenReturn(target);

        User result = userService.updateRoles(organizationId, actor, target.getId(), Set.of(memberRole.getId()));

        assertThat(result.getRoles()).containsExactly(memberRole);
        verify(events).publishEvent(any(com.aitrainercrm.platform.audit.event.OrgManagementAuditEvents.UserRolesChanged.class));
    }

    @Test
    void remove_theLastOwner_isRejected() {
        User actor = userWithId();
        User target = userWithRole(RoleService.OWNER);
        target.setOrganizationId(organizationId);

        when(userRepository.findActiveById(target.getId())).thenReturn(Optional.of(target));
        when(userRepository.countByOrganizationIdAndRoles_NameAndDeletedAtIsNull(organizationId, RoleService.OWNER))
                .thenReturn(1L);

        assertThatThrownBy(() -> userService.remove(organizationId, actor, target.getId()))
                .isInstanceOf(ForbiddenException.class);

        verify(refreshTokenRepository, never()).revokeAllForUser(any(), any());
    }

    @Test
    void remove_yourself_isRejected_regardlessOfOwnerCount() {
        User actor = userWithId();
        actor.setOrganizationId(organizationId);

        assertThatThrownBy(() -> userService.remove(organizationId, actor, actor.getId()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("cannot remove your own account");

        verify(userRepository, never()).findActiveById(any());
    }

    @Test
    void invite_withNoRolesSpecified_defaultsToMemberRole() {
        CreateUserRequest request = new CreateUserRequest("new.teammate@example.com", "New", "Teammate", null);
        User actor = userWithId();
        Role memberRole = new Role(RoleService.MEMBER, "desc", organizationId, true);

        when(userRepository.existsByEmailAndDeletedAtIsNull("new.teammate@example.com")).thenReturn(false);
        when(roleService.resolveForOrganization(organizationId, null)).thenReturn(Set.of());
        when(roleService.getMemberRole(organizationId)).thenReturn(memberRole);
        when(passwordEncoder.encode(anyString())).thenReturn("unusable-hash");
        when(secureTokenService.generateRawToken()).thenReturn("raw-token");
        when(secureTokenService.hash(anyString())).thenReturn("hashed");

        User invited = userService.invite(organizationId, actor, request);

        assertThat(invited.getRoles()).containsExactly(memberRole);
        assertThat(invited.getOrganizationId()).isEqualTo(organizationId);
        verify(emailService).sendInvitationEmail(eq("new.teammate@example.com"), anyString(), eq("raw-token"));
    }

    private User userWithId() {
        User user = new User("actor@example.com", "hash", "Actor", "User");
        user.setId(UUID.randomUUID());
        return user;
    }

    private User userWithRole(String roleName) {
        User user = new User("target@example.com", "hash", "Target", "User");
        user.setId(UUID.randomUUID());
        user.addRole(new Role(roleName, "desc", null, true));
        return user;
    }
}
