package com.aitrainercrm.platform.exercise.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A catalog entry in the org's movement library (a single named move like "Barbell Back Squat")
 * a coach references when planning a session - see V38's migration comment for why this mirrors
 * {@link com.aitrainercrm.platform.course.entity.Course}/{@code Product} rather than an
 * owner-scoped entity: an exercise is shared organization data an admin/lead coach maintains,
 * not something one rep owns, so there's no {@code ownerId} column and {@code ExerciseService}
 * does no {@code ScopeAuthorizationService} record-level check.
 */
@Entity
@Table(name = "exercises")
@Getter
@Setter
@NoArgsConstructor
public class Exercise extends BaseEntity {

    public enum Category {
        STRENGTH, CARDIO, FLEXIBILITY, MOBILITY, BALANCE, PLYOMETRIC
    }

    public enum MuscleGroup {
        CHEST, BACK, SHOULDERS, ARMS, LEGS, GLUTES, CORE, FULL_BODY
    }

    public enum Equipment {
        BARBELL, DUMBBELL, KETTLEBELL, MACHINE, CABLE, RESISTANCE_BAND, BODYWEIGHT, NONE
    }

    public enum DifficultyLevel {
        BEGINNER, INTERMEDIATE, ADVANCED
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_muscle_group", nullable = false, length = 20)
    private MuscleGroup primaryMuscleGroup;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Equipment equipment = Equipment.NONE;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty_level", nullable = false, length = 20)
    private DifficultyLevel difficultyLevel = DifficultyLevel.BEGINNER;

    @Column(name = "video_url", length = 500)
    private String videoUrl;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Exercise(UUID organizationId, String name, Category category, MuscleGroup primaryMuscleGroup) {
        this.organizationId = organizationId;
        this.name = name;
        this.category = category;
        this.primaryMuscleGroup = primaryMuscleGroup;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
