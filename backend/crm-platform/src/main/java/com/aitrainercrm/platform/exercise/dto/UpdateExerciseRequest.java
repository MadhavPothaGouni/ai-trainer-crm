package com.aitrainercrm.platform.exercise.dto;

import com.aitrainercrm.platform.exercise.entity.Exercise;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateExerciseRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @NotNull Exercise.Category category,
        @NotNull Exercise.MuscleGroup primaryMuscleGroup,
        @NotNull Exercise.Equipment equipment,
        @NotNull Exercise.DifficultyLevel difficultyLevel,
        @Size(max = 500) String videoUrl,
        boolean active) {
}
