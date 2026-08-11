package com.aitrainercrm.platform.apikey.dto;

import com.aitrainercrm.platform.apikey.entity.ApiKey;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/**
 * {@code rawKey} is {@code null} on every response except the one
 * immediately after {@code POST /api/v1/api-keys} - see
 * {@code ApiKeyService#create}'s javadoc. Every other endpoint
 * ({@code GET}, list) builds this via {@link #from(ApiKey)}, which never
 * populates it, because the raw secret was never stored anywhere to
 * populate it *from* after creation.
 */
@Builder
public record ApiKeyDto(
        UUID id,
        String name,
        String keyPrefix,
        UUID createdByUserId,
        Instant lastUsedAt,
        Instant expiresAt,
        Instant revokedAt,
        Instant createdAt,
        String rawKey) {

    public static ApiKeyDto from(ApiKey apiKey) {
        return ApiKeyDto.builder()
                .id(apiKey.getId())
                .name(apiKey.getName())
                .keyPrefix(apiKey.getKeyPrefix())
                .createdByUserId(apiKey.getCreatedByUserId())
                .lastUsedAt(apiKey.getLastUsedAt())
                .expiresAt(apiKey.getExpiresAt())
                .revokedAt(apiKey.getRevokedAt())
                .createdAt(apiKey.getCreatedAt())
                .build();
    }
}
