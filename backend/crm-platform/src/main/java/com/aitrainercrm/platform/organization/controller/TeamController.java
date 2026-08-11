package com.aitrainercrm.platform.organization.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.organization.dto.CreateTeamRequest;
import com.aitrainercrm.platform.organization.dto.TeamDto;
import com.aitrainercrm.platform.organization.dto.UpdateTeamRequest;
import com.aitrainercrm.platform.organization.entity.Team;
import com.aitrainercrm.platform.organization.service.TeamService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

/** Only ORGANIZATION scope is seeded for TEAM (V16) - single hasAuthority(...), not hasAnyAuthority(...), same style OrganizationController uses for ORGANIZATION:UPDATE:ORGANIZATION. */
@RestController
@RequestMapping("/api/v1/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @GetMapping
    @PreAuthorize("hasAuthority('TEAM:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<TeamDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<Team> page = teamService.list(principal.getOrganizationId(), pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(TeamDto::from).toList()));
    }

    @GetMapping("/{teamId}")
    @PreAuthorize("hasAuthority('TEAM:READ:ORGANIZATION')")
    public ApiResponse<TeamDto> get(@PathVariable UUID teamId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(TeamDto.from(teamService.get(principal.getOrganizationId(), teamId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('TEAM:CREATE:ORGANIZATION')")
    public ApiResponse<TeamDto> create(@Valid @RequestBody CreateTeamRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(TeamDto.from(teamService.create(principal.getOrganizationId(), request)), "Team created");
    }

    @PutMapping("/{teamId}")
    @PreAuthorize("hasAuthority('TEAM:UPDATE:ORGANIZATION')")
    public ApiResponse<TeamDto> update(
            @PathVariable UUID teamId, @Valid @RequestBody UpdateTeamRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(TeamDto.from(teamService.update(principal.getOrganizationId(), teamId, request)), "Team updated");
    }

    @DeleteMapping("/{teamId}")
    @PreAuthorize("hasAuthority('TEAM:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID teamId, @AuthenticationPrincipal UserPrincipal principal) {
        teamService.delete(principal.getOrganizationId(), teamId);
        return ApiResponse.ok(null, "Team deleted");
    }
}
