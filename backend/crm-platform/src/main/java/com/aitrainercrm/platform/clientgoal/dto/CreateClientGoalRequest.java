package com.aitrainercrm.platform.clientgoal.dto;

import com.aitrainercrm.platform.clientgoal.entity.ClientGoal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateClientGoalRequest(
        @NotNull UUID contactId,
        @NotBlank @Size(max = 200) String title,
        @NotNull ClientGoal.GoalType goalType,
        @Size(max = 30) String metricUnit,
        BigDecimal startValue,
        BigDecimal targetValue,
        BigDecimal currentValue,
        LocalDate targetDate,
        @Size(max = 2000) String notes,

        /** Null defaults to the creator - see AccountService#resolveOwner for the identical rule applied here. */
        UUID ownerId) {
}
