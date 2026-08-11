package com.aitrainercrm.platform.organization.service;

import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.organization.dto.CreateTeamRequest;
import com.aitrainercrm.platform.organization.dto.UpdateTeamRequest;
import com.aitrainercrm.platform.organization.entity.Team;
import com.aitrainercrm.platform.organization.repository.TeamRepository;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Team CRUD - the management API {@link TeamRepository}'s javadoc has flagged as missing since
 * V1. Same shared-org-resource pattern as {@code CampaignService}: TEAM only has ORGANIZATION
 * scope seeded (V16), so there's no {@code ScopeAuthorizationService} call here - the
 * controller's static {@code @PreAuthorize} is the whole authorization story, same as Campaign/
 * Product/Order.
 *
 * <p>Deleting a team never touches the users on it - {@link Team#getDeletedAt()} just hides it
 * from listings; anyone still pointing at it via {@code User#getTeamId()} keeps a valid (if
 * now-hidden) team id rather than being silently reassigned or blocked by an FK violation. See
 * V16's migration comment.
 */
@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<Team> list(UUID organizationId, Pageable pageable) {
        return teamRepository.findByOrganizationIdAndDeletedAtIsNull(organizationId, pageable);
    }

    @Transactional(readOnly = true)
    public Team get(UUID organizationId, UUID teamId) {
        return findOrThrow(organizationId, teamId);
    }

    @Transactional
    public Team create(UUID organizationId, CreateTeamRequest request) {
        assertLeadInOrganization(organizationId, request.leadUserId());

        Team team = new Team(organizationId, request.name(), request.department());
        team.setLeadUserId(request.leadUserId());
        return teamRepository.save(team);
    }

    @Transactional
    public Team update(UUID organizationId, UUID teamId, UpdateTeamRequest request) {
        Team team = findOrThrow(organizationId, teamId);
        assertLeadInOrganization(organizationId, request.leadUserId());

        team.setName(request.name());
        team.setDepartment(request.department());
        team.setLeadUserId(request.leadUserId());
        return teamRepository.save(team);
    }

    @Transactional
    public void delete(UUID organizationId, UUID teamId) {
        Team team = findOrThrow(organizationId, teamId);
        team.setDeletedAt(Instant.now());
        teamRepository.save(team);
    }

    private Team findOrThrow(UUID organizationId, UUID teamId) {
        return teamRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(teamId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Team", teamId));
    }

    private void assertLeadInOrganization(UUID organizationId, UUID leadUserId) {
        if (leadUserId == null) return;
        boolean exists = userRepository.findActiveById(leadUserId).map(u -> organizationId.equals(u.getOrganizationId())).orElse(false);
        if (!exists) {
            throw new ResourceNotFoundException("User", leadUserId);
        }
    }
}
