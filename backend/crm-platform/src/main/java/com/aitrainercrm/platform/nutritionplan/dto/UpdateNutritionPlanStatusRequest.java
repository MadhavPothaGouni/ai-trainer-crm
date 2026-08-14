package com.aitrainercrm.platform.nutritionplan.dto;

import com.aitrainercrm.platform.nutritionplan.entity.NutritionPlan;
import jakarta.validation.constraints.NotNull;

public record UpdateNutritionPlanStatusRequest(@NotNull NutritionPlan.Status status) {
}
