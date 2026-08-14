package com.aitrainercrm.platform.nutritionplan.dto;

import com.aitrainercrm.platform.nutritionplan.entity.NutritionPlan;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

@Builder
public record NutritionPlanDto(
        UUID id,
        UUID contactId,
        UUID ownerId,
        String title,
        Integer dailyCalorieTarget,
        Integer proteinTargetGrams,
        Integer carbTargetGrams,
        Integer fatTargetGrams,
        LocalDate startDate,
        LocalDate endDate,
        NutritionPlan.Status status,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static NutritionPlanDto from(NutritionPlan plan) {
        return NutritionPlanDto.builder()
                .id(plan.getId())
                .contactId(plan.getContactId())
                .ownerId(plan.getOwnerId())
                .title(plan.getTitle())
                .dailyCalorieTarget(plan.getDailyCalorieTarget())
                .proteinTargetGrams(plan.getProteinTargetGrams())
                .carbTargetGrams(plan.getCarbTargetGrams())
                .fatTargetGrams(plan.getFatTargetGrams())
                .startDate(plan.getStartDate())
                .endDate(plan.getEndDate())
                .status(plan.getStatus())
                .notes(plan.getNotes())
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .build();
    }
}
