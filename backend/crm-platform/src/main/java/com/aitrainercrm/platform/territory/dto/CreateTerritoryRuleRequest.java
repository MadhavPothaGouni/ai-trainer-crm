package com.aitrainercrm.platform.territory.dto;

import com.aitrainercrm.platform.territory.entity.TerritoryRule;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Exactly one of assignToUserId/assignToTeamId must be set - TerritoryRuleService rejects both-set and neither-set. See TerritoryRule's javadoc for which matchField values are valid for which targetResource. */
public record CreateTerritoryRuleRequest(
        @NotBlank @Size(max = 150) String name,
        @NotNull TerritoryRule.TargetResource targetResource,
        @NotNull TerritoryRule.MatchField matchField,
        @NotNull TerritoryRule.MatchOperator matchOperator,
        @NotBlank @Size(max = 200) String matchValue,
        int priority,
        UUID assignToUserId,
        UUID assignToTeamId) {
}
