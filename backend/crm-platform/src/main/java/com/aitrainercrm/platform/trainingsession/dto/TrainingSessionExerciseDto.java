package com.aitrainercrm.platform.trainingsession.dto;

import com.aitrainercrm.platform.trainingsession.entity.TrainingSessionExercise;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;

@Builder
public record TrainingSessionExerciseDto(
        UUID id,
        UUID exerciseId,
        String exerciseName,
        int sequenceOrder,
        int setsCompleted,
        String repsCompleted,
        BigDecimal weightValue,
        String weightUnit,
        String notes) {

    public static TrainingSessionExerciseDto from(TrainingSessionExercise exercise) {
        return TrainingSessionExerciseDto.builder()
                .id(exercise.getId())
                .exerciseId(exercise.getExerciseId())
                .exerciseName(exercise.getExerciseName())
                .sequenceOrder(exercise.getSequenceOrder())
                .setsCompleted(exercise.getSetsCompleted())
                .repsCompleted(exercise.getRepsCompleted())
                .weightValue(exercise.getWeightValue())
                .weightUnit(exercise.getWeightUnit())
                .notes(exercise.getNotes())
                .build();
    }
}
