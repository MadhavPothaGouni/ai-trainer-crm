package com.aitrainercrm.platform.nutritionplan.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record CreateNutritionPlanRequest(
        @NotNull UUID contactId,
        @NotBlank @Size(max = 200) String title,
        @Min(0) Integer dailyCalorieTarget,
        @Min(0) Integer proteinTargetGrams,
        @Min(0) Integer carbTargetGrams,
        @Min(0) Integer fatTargetGrams,
        LocalDate startDate,
        LocalDate endDate,
        @Size(max = 2000) String notes,

        /** Null defaults to the creator - see AccountService#resolveOwner for the identical rule applied here. */
        UUID ownerId) {
}
