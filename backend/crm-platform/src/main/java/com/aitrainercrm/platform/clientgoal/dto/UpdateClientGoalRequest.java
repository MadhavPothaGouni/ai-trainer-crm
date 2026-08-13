package com.aitrainercrm.platform.clientgoal.dto;

import com.aitrainercrm.platform.clientgoal.entity.ClientGoal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Status is deliberately not editable here - see UpdateClientGoalStatusRequest / PATCH .../status, same reasoning UpdateContractRequest documents. */
public record UpdateClientGoalRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull ClientGoal.GoalType goalType,
        @Size(max = 30) String metricUnit,
        BigDecimal startValue,
        BigDecimal targetValue,
        BigDecimal currentValue,
        LocalDate targetDate,
        @Size(max = 2000) String notes) {
}
