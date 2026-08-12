package com.aitrainercrm.platform.leadscoring.dto;

import com.aitrainercrm.platform.leadscoring.entity.LeadScoringRule;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** points may be zero or negative - LeadScoringRuleService doesn't reject either, since a 0-point rule (tracked for matchCount visibility only) and a penalty rule are both legitimate. */
public record CreateLeadScoringRuleRequest(
        @NotBlank @Size(max = 150) String name,
        @NotNull LeadScoringRule.MatchField matchField,
        @NotNull LeadScoringRule.MatchOperator matchOperator,
        @NotBlank @Size(max = 200) String matchValue,
        int points) {
}
