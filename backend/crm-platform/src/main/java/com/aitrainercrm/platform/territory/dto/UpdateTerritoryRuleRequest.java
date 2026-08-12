package com.aitrainercrm.platform.territory.dto;

import com.aitrainercrm.platform.territory.entity.TerritoryRule;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** targetResource is not editable here - see TerritoryRuleService#update's javadoc for why changing what a rule targets is modeled as retire-and-recreate. */
public record UpdateTerritoryRuleRequest(
        @NotBlank @Size(max = 150) String name,
        @NotNull TerritoryRule.MatchField matchField,
        @NotNull TerritoryRule.MatchOperator matchOperator,
        @NotBlank @Size(max = 200) String matchValue,
        int priority,
        UUID assignToUserId,
        UUID assignToTeamId,
        boolean active) {
}
