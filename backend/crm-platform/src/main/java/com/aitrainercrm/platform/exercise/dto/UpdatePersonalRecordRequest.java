package com.aitrainercrm.platform.exercise.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record UpdatePersonalRecordRequest(@NotNull @Positive BigDecimal value, Instant achievedAt, @Size(max = 2000) String notes) {
}
