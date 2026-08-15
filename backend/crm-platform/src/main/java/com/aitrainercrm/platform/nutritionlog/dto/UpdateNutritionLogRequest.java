package com.aitrainercrm.platform.nutritionlog.dto;

import com.aitrainercrm.platform.nutritionlog.entity.NutritionLog;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record UpdateNutritionLogRequest(
        @NotNull Instant loggedAt,
        @NotNull NutritionLog.MealType mealType,
        @PositiveOrZero Integer calories,
        @PositiveOrZero BigDecimal proteinGrams,
        @PositiveOrZero BigDecimal carbGrams,
        @PositiveOrZero BigDecimal fatGrams,
        @Size(max = 2000) String notes) {
}
