package com.aitrainercrm.platform.nutritionlog.dto;

import com.aitrainercrm.platform.nutritionlog.entity.NutritionLog;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record NutritionLogDto(
        UUID id,
        UUID contactId,
        UUID ownerId,
        Instant loggedAt,
        NutritionLog.MealType mealType,
        Integer calories,
        BigDecimal proteinGrams,
        BigDecimal carbGrams,
        BigDecimal fatGrams,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static NutritionLogDto from(NutritionLog log) {
        return new NutritionLogDto(
                log.getId(),
                log.getContactId(),
                log.getOwnerId(),
                log.getLoggedAt(),
                log.getMealType(),
                log.getCalories(),
                log.getProteinGrams(),
                log.getCarbGrams(),
                log.getFatGrams(),
                log.getNotes(),
                log.getCreatedAt(),
                log.getUpdatedAt());
    }
}
