package com.aitrainercrm.platform.user.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.dto.CreateUserRequest;
import com.aitrainercrm.platform.user.dto.UpdateProfileRequest;
import com.aitrainercrm.platform.user.dto.UpdateUserRolesRequest;
import com.aitrainercrm.platform.user.dto.UpdateUserStatusRequest;
import com.aitrainercrm.platform.user.dto.UpdateUserTeamRequest;
import com.aitrainercrm.platform.user.dto.UserDto;
import com.aitrainercrm.platform.user.entity.User;
import com.aitrainercrm.platform.user.repository.UserRepository;
import com.aitrainercrm.platform.user.service.UserService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Every endpoint here is scoped to the caller's own organization -
 * {@code principal.getOrganizationId()} off the JWT, never a
 * client-supplied org id - so there's no path to reading or mutating a
 * different tenant's users. The three destructive-ish operations
 * (role change, status change, removal) all guard against ever leaving
 * an organization with zero OWNERs - see UserService for where that's
 * enforced.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('USER:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<UserDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<User> page = userService.list(principal.getOrganizationId(), pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(UserDto::from).toList()));
    }

    @GetMapping("/me")
    public ApiResponse<UserDto> me(@AuthenticationPrincipal UserPrincipal principal) {
        User user = userRepository.findActiveById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", principal.getId()));
        return ApiResponse.ok(UserDto.from(user));
    }

    @PatchMapping("/me")
    public ApiResponse<UserDto> updateMe(
            @Valid @RequestBody UpdateProfileRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        User user = userService.updateOwnProfile(principal.getId(), request);
        return ApiResponse.ok(UserDto.from(user), "Profile updated");
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority('USER:READ:ORGANIZATION')")
    public ApiResponse<UserDto> get(@PathVariable UUID userId, @AuthenticationPrincipal UserPrincipal principal) {
        User user = userService.get(principal.getOrganizationId(), userId);
        return ApiResponse.ok(UserDto.from(user));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('USER:CREATE:ORGANIZATION')")
    public ApiResponse<UserDto> invite(
            @Valid @RequestBody CreateUserRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        User actor = currentActor(principal);
        User invited = userService.invite(principal.getOrganizationId(), actor, request);
        return ApiResponse.ok(UserDto.from(invited), "Invitation sent");
    }

    @PatchMapping("/{userId}/roles")
    @PreAuthorize("hasAuthority('USER:UPDATE:ORGANIZATION')")
    public ApiResponse<UserDto> updateRoles(
            @PathVariable UUID userId, @Valid @RequestBody UpdateUserRolesRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        User actor = currentActor(principal);
        User updated = userService.updateRoles(principal.getOrganizationId(), actor, userId, request.roleIds());
        return ApiResponse.ok(UserDto.from(updated), "Roles updated");
    }

    @PatchMapping("/{userId}/team")
    @PreAuthorize("hasAuthority('USER:UPDATE:ORGANIZATION')")
    public ApiResponse<UserDto> updateTeam(
            @PathVariable UUID userId, @Valid @RequestBody UpdateUserTeamRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        User actor = currentActor(principal);
        User updated = userService.updateTeam(principal.getOrganizationId(), actor, userId, request.teamId());
        return ApiResponse.ok(UserDto.from(updated), "Team updated");
    }

    @PatchMapping("/{userId}/status")
    @PreAuthorize("hasAuthority('USER:UPDATE:ORGANIZATION')")
    public ApiResponse<UserDto> updateStatus(
            @PathVariable UUID userId, @Valid @RequestBody UpdateUserStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        User actor = currentActor(principal);
        User updated = userService.updateStatus(principal.getOrganizationId(), actor, userId, request.status());
        return ApiResponse.ok(UserDto.from(updated), "Status updated");
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority('USER:DELETE:ORGANIZATION')")
    public ApiResponse<Void> remove(@PathVariable UUID userId, @AuthenticationPrincipal UserPrincipal principal) {
        User actor = currentActor(principal);
        userService.remove(principal.getOrganizationId(), actor, userId);
        return ApiResponse.ok(null, "User removed");
    }

    /** UserService's guardrails (self-removal, last-OWNER checks) work against the actor's own User row, not just their JWT claims, so every write endpoint resolves it once here. */
    private User currentActor(UserPrincipal principal) {
        return userRepository.findActiveById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", principal.getId()));
    }
}
