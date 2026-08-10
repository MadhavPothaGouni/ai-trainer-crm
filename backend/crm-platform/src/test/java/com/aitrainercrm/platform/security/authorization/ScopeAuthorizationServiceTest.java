package com.aitrainercrm.platform.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.organization.entity.Team;
import com.aitrainercrm.platform.organization.repository.TeamRepository;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.entity.User;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The RBAC decision that actually matters for the CRM domain: given a
 * caller's flattened JWT authorities and a record's owner, is this
 * particular access allowed? {@code @PreAuthorize} on a controller can't
 * express this (it never sees the record), so this class is where the
 * real enforcement lives - these tests are the primary defense against a
 * scope check silently degrading into "allow everything" or "deny
 * everything."
 */
@ExtendWith(MockitoExtension.class)
class ScopeAuthorizationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private TeamRepository teamRepository;

    private ScopeAuthorizationService service;
    private UUID organizationId;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        service = new ScopeAuthorizationService(userRepository, teamRepository);
        organizationId = UUID.randomUUID();
        actorId = UUID.randomUUID();
    }

    private UserPrincipal principalWith(String... authorities) {
        return new UserPrincipal(actorId, "actor@example.com", organizationId, List.of(authorities));
    }

    @Test
    void highestGranted_returnsOrganizationWhenGranted() {
        UserPrincipal principal = principalWith("LEAD:READ:OWN", "LEAD:READ:ORGANIZATION");
        assertThat(service.highestGranted(principal, Permission.Resource.LEAD, Permission.Action.READ))
                .isEqualTo(ScopeAuthorizationService.Access.ORGANIZATION);
    }

    @Test
    void highestGranted_prefersTeamOverOwnWhenBothGranted() {
        // Mirrors what RoleService actually seeds for MEMBER: both OWN and TEAM together.
        UserPrincipal principal = principalWith("LEAD:READ:OWN", "LEAD:READ:TEAM");
        assertThat(service.highestGranted(principal, Permission.Resource.LEAD, Permission.Action.READ))
                .isEqualTo(ScopeAuthorizationService.Access.TEAM);
    }

    @Test
    void highestGranted_returnsNoneWhenNoMatchingAuthorityAtAll() {
        UserPrincipal principal = principalWith("CONTACT:READ:ORGANIZATION"); // wrong resource entirely
        assertThat(service.highestGranted(principal, Permission.Resource.LEAD, Permission.Action.READ))
                .isEqualTo(ScopeAuthorizationService.Access.NONE);
    }

    @Test
    void assertCanAccess_organizationScope_allowsAnyRecordRegardlessOfOwner() {
        UserPrincipal principal = principalWith("ACCOUNT:READ:ORGANIZATION");
        service.assertCanAccess(principal, Permission.Resource.ACCOUNT, Permission.Action.READ, UUID.randomUUID());
        // No exception - that's the assertion.
    }

    @Test
    void assertCanAccess_ownScope_allowsRecordTheCallerOwns() {
        UserPrincipal principal = principalWith("ACCOUNT:READ:OWN");
        service.assertCanAccess(principal, Permission.Resource.ACCOUNT, Permission.Action.READ, actorId);
    }

    @Test
    void assertCanAccess_ownScope_deniesRecordSomeoneElseOwns() {
        UserPrincipal principal = principalWith("ACCOUNT:READ:OWN");
        UUID someoneElse = UUID.randomUUID();

        assertThatThrownBy(() -> service.assertCanAccess(principal, Permission.Resource.ACCOUNT, Permission.Action.READ, someoneElse))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void assertCanAccess_noAuthorityAtAll_deniesEvenTheCallersOwnRecord() {
        UserPrincipal principal = principalWith(); // holds nothing for ACCOUNT:READ:*
        assertThatThrownBy(() -> service.assertCanAccess(principal, Permission.Resource.ACCOUNT, Permission.Action.READ, actorId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void assertCanAccess_teamScope_allowsRecordOwnedBySomeoneOnTheSameTeam() {
        UUID teamId = UUID.randomUUID();
        UUID teammateOwnerId = UUID.randomUUID();
        UserPrincipal principal = principalWith("ACCOUNT:READ:TEAM");

        stubUserTeam(actorId, teamId);
        stubUserTeam(teammateOwnerId, teamId);

        service.assertCanAccess(principal, Permission.Resource.ACCOUNT, Permission.Action.READ, teammateOwnerId);
    }

    @Test
    void assertCanAccess_teamScope_deniesRecordOwnedByADifferentTeam() {
        UserPrincipal principal = principalWith("ACCOUNT:READ:TEAM");
        UUID otherTeamOwnerId = UUID.randomUUID();

        stubUserTeam(actorId, UUID.randomUUID());
        stubUserTeam(otherTeamOwnerId, UUID.randomUUID());

        assertThatThrownBy(() -> service.assertCanAccess(principal, Permission.Resource.ACCOUNT, Permission.Action.READ, otherTeamOwnerId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void assertCanAccess_teamScope_deniesWhenCallerIsntOnATeamAtAll() {
        // The realistic case today: nothing ever sets User#teamId, so this is what every
        // MEMBER's TEAM-scope check actually resolves to right now - safely degrading to
        // OWN-equivalent rather than either granting or blanket-denying everyone.
        UserPrincipal principal = principalWith("ACCOUNT:READ:TEAM");
        UUID otherOwnerId = UUID.randomUUID();

        stubUserTeam(actorId, null);
        stubUserTeam(otherOwnerId, UUID.randomUUID());

        assertThatThrownBy(() -> service.assertCanAccess(principal, Permission.Resource.ACCOUNT, Permission.Action.READ, otherOwnerId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void assertCanAccess_departmentScope_allowsRecordOwnedByATeammateInTheSameDepartment() {
        UUID actorTeamId = UUID.randomUUID();
        UUID ownerTeamId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UserPrincipal principal = principalWith("ACCOUNT:READ:DEPARTMENT");

        stubUserTeam(actorId, actorTeamId);
        stubUserTeam(ownerId, ownerTeamId);
        stubTeamDepartment(actorTeamId, "Sales");
        stubTeamDepartment(ownerTeamId, "Sales");

        service.assertCanAccess(principal, Permission.Resource.ACCOUNT, Permission.Action.READ, ownerId);
    }

    @Test
    void assertCanAccess_departmentScope_deniesRecordOwnedByADifferentDepartment() {
        UUID actorTeamId = UUID.randomUUID();
        UUID ownerTeamId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UserPrincipal principal = principalWith("ACCOUNT:READ:DEPARTMENT");

        stubUserTeam(actorId, actorTeamId);
        stubUserTeam(ownerId, ownerTeamId);
        stubTeamDepartment(actorTeamId, "Sales");
        stubTeamDepartment(ownerTeamId, "Support");

        assertThatThrownBy(() -> service.assertCanAccess(principal, Permission.Resource.ACCOUNT, Permission.Action.READ, ownerId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void visibleOwnerIds_organizationScope_returnsEmptyMeaningNoFilterNeeded() {
        UserPrincipal principal = principalWith("LEAD:READ:ORGANIZATION");
        assertThat(service.visibleOwnerIds(principal, Permission.Resource.LEAD, Permission.Action.READ)).isEmpty();
    }

    @Test
    void visibleOwnerIds_ownScope_returnsExactlyTheCallersOwnId() {
        UserPrincipal principal = principalWith("LEAD:READ:OWN");
        assertThat(service.visibleOwnerIds(principal, Permission.Resource.LEAD, Permission.Action.READ))
                .contains(Set.of(actorId));
    }

    @Test
    void visibleOwnerIds_teamScope_returnsEveryoneOnTheCallersTeam() {
        UUID teamId = UUID.randomUUID();
        UUID teammateId = UUID.randomUUID();
        UserPrincipal principal = principalWith("LEAD:READ:TEAM");

        stubUserTeam(actorId, teamId);
        when(userRepository.findIdsByOrganizationIdAndTeamId(organizationId, teamId)).thenReturn(List.of(actorId, teammateId));

        assertThat(service.visibleOwnerIds(principal, Permission.Resource.LEAD, Permission.Action.READ))
                .contains(Set.of(actorId, teammateId));
    }

    @Test
    void visibleOwnerIds_noAuthorityAtAll_throwsForbidden() {
        UserPrincipal principal = principalWith();
        assertThatThrownBy(() -> service.visibleOwnerIds(principal, Permission.Resource.LEAD, Permission.Action.READ))
                .isInstanceOf(ForbiddenException.class);
    }

    private void stubUserTeam(UUID userId, UUID teamId) {
        User user = new User("user-%s@example.com".formatted(userId), "hash", "First", "Last");
        user.setId(userId);
        user.setOrganizationId(organizationId);
        user.setTeamId(teamId);
        lenient().when(userRepository.findActiveById(eq(userId))).thenReturn(Optional.of(user));
    }

    private void stubTeamDepartment(UUID teamId, String department) {
        Team team = new Team(organizationId, "Team " + teamId, department);
        team.setId(teamId);
        lenient().when(teamRepository.findByIdAndOrganizationId(eq(teamId), any())).thenReturn(Optional.of(team));
    }
}
