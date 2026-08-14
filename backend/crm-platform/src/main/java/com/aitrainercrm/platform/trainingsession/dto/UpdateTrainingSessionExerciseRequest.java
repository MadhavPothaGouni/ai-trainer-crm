package com.aitrainercrm.platform.trainingsession.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record UpdateTrainingSessionExerciseRequest(
        UUID exerciseId,
        @NotBlank @Size(max = 200) String exerciseName,
        @Min(1) int setsCompleted,
        @NotBlank @Size(max = 50) String repsCompleted,
        @DecimalMin(value = "0", inclusive = true) BigDecimal weightValue,
        @Size(max = 10) String weightUnit,
        @Size(max = 500) String notes) {
}
