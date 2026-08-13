package com.aitrainercrm.platform.clientgoal.dto;

import com.aitrainercrm.platform.clientgoal.entity.ClientGoal;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

@Builder
public record ClientGoalDto(
        UUID id,
        UUID contactId,
        UUID ownerId,
        String title,
        ClientGoal.GoalType goalType,
        String metricUnit,
        BigDecimal startValue,
        BigDecimal targetValue,
        BigDecimal currentValue,
        LocalDate targetDate,
        ClientGoal.Status status,
        Instant achievedAt,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static ClientGoalDto from(ClientGoal goal) {
        return ClientGoalDto.builder()
                .id(goal.getId())
                .contactId(goal.getContactId())
                .ownerId(goal.getOwnerId())
                .title(goal.getTitle())
                .goalType(goal.getGoalType())
                .metricUnit(goal.getMetricUnit())
                .startValue(goal.getStartValue())
                .targetValue(goal.getTargetValue())
                .currentValue(goal.getCurrentValue())
                .targetDate(goal.getTargetDate())
                .status(goal.getStatus())
                .achievedAt(goal.getAchievedAt())
                .notes(goal.getNotes())
                .createdAt(goal.getCreatedAt())
                .updatedAt(goal.getUpdatedAt())
                .build();
    }
}
