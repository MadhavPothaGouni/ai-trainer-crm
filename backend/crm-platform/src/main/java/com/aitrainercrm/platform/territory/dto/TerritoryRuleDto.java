package com.aitrainercrm.platform.territory.dto;

import com.aitrainercrm.platform.territory.entity.TerritoryRule;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record TerritoryRuleDto(
        UUID id,
        String name,
        TerritoryRule.TargetResource targetResource,
        TerritoryRule.MatchField matchField,
        TerritoryRule.MatchOperator matchOperator,
        String matchValue,
        int priority,
        UUID assignToUserId,
        UUID assignToTeamId,
        boolean active,
        int matchCount,
        Instant lastMatchedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static TerritoryRuleDto from(TerritoryRule rule) {
        return TerritoryRuleDto.builder()
                .id(rule.getId())
                .name(rule.getName())
                .targetResource(rule.getTargetResource())
                .matchField(rule.getMatchField())
                .matchOperator(rule.getMatchOperator())
                .matchValue(rule.getMatchValue())
                .priority(rule.getPriority())
                .assignToUserId(rule.getAssignToUserId())
                .assignToTeamId(rule.getAssignToTeamId())
                .active(rule.isActive())
                .matchCount(rule.getMatchCount())
                .lastMatchedAt(rule.getLastMatchedAt())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }
}
