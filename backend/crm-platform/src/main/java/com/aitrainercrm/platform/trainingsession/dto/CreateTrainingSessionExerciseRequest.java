package com.aitrainercrm.platform.trainingsession.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * exerciseId is optional (a coach can log a movement that isn't in the catalog yet), but
 * exerciseName is always required from the caller - same reasoning
 * {@code CreateQuoteLineItemRequest}'s javadoc gives for description/unitPrice always being
 * required even when productId is set. The frontend prefills exerciseName from the selected
 * catalog exercise; the caller can still override it before saving.
 */
public record CreateTrainingSessionExerciseRequest(
        UUID exerciseId,
        @NotBlank @Size(max = 200) String exerciseName,
        @Min(1) int setsCompleted,
        @NotBlank @Size(max = 50) String repsCompleted,
        @DecimalMin(value = "0", inclusive = true) BigDecimal weightValue,
        @Size(max = 10) String weightUnit,
        @Size(max = 500) String notes) {
}
