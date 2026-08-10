package com.aitrainercrm.platform.auth.dto;

import java.util.UUID;
import lombok.Builder;

@Builder
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        int expiresInSeconds,
        UUID userId,
        String email,
        String fullName) {

    public static AuthResponse of(String accessToken, String refreshToken, int expiresInSeconds, UUID userId, String email, String fullName) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresInSeconds(expiresInSeconds)
                .userId(userId)
                .email(email)
                .fullName(fullName)
                .build();
    }
}
