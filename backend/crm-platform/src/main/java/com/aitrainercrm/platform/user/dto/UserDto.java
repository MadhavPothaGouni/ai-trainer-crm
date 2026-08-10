package com.aitrainercrm.platform.user.dto;

import com.aitrainercrm.platform.user.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
public record UserDto(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String fullName,
        String phone,
        String avatarUrl,
        String status,
        boolean emailVerified,
        boolean mfaEnabled,
        UUID teamId,
        UUID managerId,
        List<String> roles,
        Instant lastLoginAt,
        Instant createdAt) {

    public static UserDto from(User user) {
        return UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus().name())
                .emailVerified(user.isEmailVerified())
                .mfaEnabled(user.isMfaEnabled())
                .teamId(user.getTeamId())
                .managerId(user.getManagerId())
                .roles(user.getRoles().stream().map(role -> role.getName()).sorted().toList())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
