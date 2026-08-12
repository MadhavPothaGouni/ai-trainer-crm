package com.aitrainercrm.platform.dedupe.dto;

import com.aitrainercrm.platform.dedupe.entity.DuplicateMatch;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record DuplicateMatchDto(
        UUID id,
        DuplicateMatch.EntityType entityType,
        UUID recordAId,
        UUID recordBId,
        DuplicateMatch.MatchReason matchReason,
        DuplicateMatch.Status status,
        UUID survivorId,
        UUID absorbedId,
        UUID resolvedByUserId,
        Instant resolvedAt,
        Instant createdAt) {

    public static DuplicateMatchDto from(DuplicateMatch match) {
        return DuplicateMatchDto.builder()
                .id(match.getId())
                .entityType(match.getEntityType())
                .recordAId(match.getRecordAId())
                .recordBId(match.getRecordBId())
                .matchReason(match.getMatchReason())
                .status(match.getStatus())
                .survivorId(match.getSurvivorId())
                .absorbedId(match.getAbsorbedId())
                .resolvedByUserId(match.getResolvedByUserId())
                .resolvedAt(match.getResolvedAt())
                .createdAt(match.getCreatedAt())
                .build();
    }
}
