package com.aitrainercrm.platform.opportunity.dto;

import com.aitrainercrm.platform.opportunity.entity.Opportunity;
import jakarta.validation.constraints.NotNull;

public record UpdateOpportunityStageRequest(@NotNull Opportunity.Stage stage) {
}
