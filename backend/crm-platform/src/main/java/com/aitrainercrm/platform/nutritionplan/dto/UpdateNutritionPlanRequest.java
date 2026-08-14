package com.aitrainercrm.platform.nutritionplan.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Status is deliberately not editable here - see UpdateNutritionPlanStatusRequest / PATCH .../status, same reasoning UpdateClientGoalRequest documents. */
public record UpdateNutritionPlanRequest(
        @NotBlank @Size(max = 200) String title,
        @Min(0) Integer dailyCalorieTarget,
        @Min(0) Integer proteinTargetGrams,
        @Min(0) Integer carbTargetGrams,
        @Min(0) Integer fatTargetGrams,
        LocalDate startDate,
        LocalDate endDate,
        @Size(max = 2000) String notes) {
}
