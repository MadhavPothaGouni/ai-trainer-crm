package com.aitrainercrm.platform.leadscoring.dto;

import com.aitrainercrm.platform.leadscoring.entity.LeadScoringRule;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record LeadScoringRuleDto(
        UUID id,
        String name,
        LeadScoringRule.MatchField matchField,
        LeadScoringRule.MatchOperator matchOperator,
        String matchValue,
        int points,
        boolean active,
        int matchCount,
        Instant lastMatchedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static LeadScoringRuleDto from(LeadScoringRule rule) {
        return LeadScoringRuleDto.builder()
                .id(rule.getId())
                .name(rule.getName())
                .matchField(rule.getMatchField())
                .matchOperator(rule.getMatchOperator())
                .matchValue(rule.getMatchValue())
                .points(rule.getPoints())
                .active(rule.isActive())
                .matchCount(rule.getMatchCount())
                .lastMatchedAt(rule.getLastMatchedAt())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }
}
