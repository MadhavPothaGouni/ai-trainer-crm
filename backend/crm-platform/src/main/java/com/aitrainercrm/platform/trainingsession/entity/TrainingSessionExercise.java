package com.aitrainercrm.platform.trainingsession.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One exercise actually performed within a {@link TrainingSession} - the connective tissue between
 * this module and {@link com.aitrainercrm.platform.exercise.entity.Exercise} that both V37's and
 * V38's migration comments flagged as deliberately unbuilt. Same "real child row, no permission of
 * its own, managed entirely through the parent's service" shape {@code QuoteLineItem} uses for
 * {@code Quote} - see V39's migration comment for the full reasoning, including why {@link
 * #exerciseId} is nullable/uncascaded and {@link #exerciseName} is a snapshot rather than a live
 * join.
 */
@Entity
@Table(name = "training_session_exercises")
@Getter
@Setter
@NoArgsConstructor
public class TrainingSessionExercise extends BaseEntity {

    @Column(name = "training_session_id", nullable = false)
    private UUID trainingSessionId;

    @Column(name = "exercise_id")
    private UUID exerciseId;

    @Column(name = "exercise_name", nullable = false, length = 200)
    private String exerciseName;

    @Column(name = "sequence_order", nullable = false)
    private int sequenceOrder;

    @Column(name = "sets_completed", nullable = false)
    private int setsCompleted = 1;

    /** Freeform per-set string like "12,10,8" - reps routinely vary set to set, so a rigid integer would lose real coaching detail. */
    @Column(name = "reps_completed", nullable = false, length = 50)
    private String repsCompleted;

    @Column(name = "weight_value", precision = 6, scale = 2)
    private BigDecimal weightValue;

    @Column(name = "weight_unit", length = 10)
    private String weightUnit;

    @Column(length = 500)
    private String notes;

    public TrainingSessionExercise(UUID trainingSessionId, String exerciseName, int sequenceOrder, int setsCompleted, String repsCompleted) {
        this.trainingSessionId = trainingSessionId;
        this.exerciseName = exerciseName;
        this.sequenceOrder = sequenceOrder;
        this.setsCompleted = setsCompleted;
        this.repsCompleted = repsCompleted;
    }
}
