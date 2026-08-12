package com.aitrainercrm.platform.leadscoring.dto;

import com.aitrainercrm.platform.leadscoring.entity.LeadScoringRule;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Everything about a rule, including matchField, can change freely after creation - unlike TerritoryRule.targetResource, matchField was never load-bearing for anything else in this table, so there's no reason to lock it. */
public record UpdateLeadScoringRuleRequest(
        @NotBlank @Size(max = 150) String name,
        @NotNull LeadScoringRule.MatchField matchField,
        @NotNull LeadScoringRule.MatchOperator matchOperator,
        @NotBlank @Size(max = 200) String matchValue,
        int points,
        boolean active) {
}
