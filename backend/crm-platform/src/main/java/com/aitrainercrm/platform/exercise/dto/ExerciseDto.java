package com.aitrainercrm.platform.exercise.dto;

import com.aitrainercrm.platform.exercise.entity.Exercise;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record ExerciseDto(
        UUID id,
        String name,
        String description,
        Exercise.Category category,
        Exercise.MuscleGroup primaryMuscleGroup,
        Exercise.Equipment equipment,
        Exercise.DifficultyLevel difficultyLevel,
        String videoUrl,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static ExerciseDto from(Exercise exercise) {
        return ExerciseDto.builder()
                .id(exercise.getId())
                .name(exercise.getName())
                .description(exercise.getDescription())
                .category(exercise.getCategory())
                .primaryMuscleGroup(exercise.getPrimaryMuscleGroup())
                .equipment(exercise.getEquipment())
                .difficultyLevel(exercise.getDifficultyLevel())
                .videoUrl(exercise.getVideoUrl())
                .active(exercise.isActive())
                .createdAt(exercise.getCreatedAt())
                .updatedAt(exercise.getUpdatedAt())
                .build();
    }
}
