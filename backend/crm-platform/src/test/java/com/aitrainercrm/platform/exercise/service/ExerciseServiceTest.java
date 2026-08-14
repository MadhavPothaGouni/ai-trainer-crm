package com.aitrainercrm.platform.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.common.exception.DuplicateResourceException;
import com.aitrainercrm.platform.exercise.dto.CreateExerciseRequest;
import com.aitrainercrm.platform.exercise.dto.UpdateExerciseRequest;
import com.aitrainercrm.platform.exercise.entity.Exercise;
import com.aitrainercrm.platform.exercise.repository.ExerciseRepository;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** See {@link ExerciseService}'s javadoc for the shape this mirrors ({@code CourseService}) - no ScopeAuthorizationService involved since EXERCISE has no OWN scope. */
@ExtendWith(MockitoExtension.class)
class ExerciseServiceTest {

    @Mock private ExerciseRepository exerciseRepository;
    @Mock private ApplicationEventPublisher events;

    private ExerciseService service;

    private final UUID organizationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ExerciseService(exerciseRepository, events);
    }

    private UserPrincipal principal() {
        return new UserPrincipal(UUID.randomUUID(), "coach@example.com", organizationId, List.of());
    }

    private CreateExerciseRequest createRequest(String name) {
        return new CreateExerciseRequest(
                name, "Classic lower-body strength movement", Exercise.Category.STRENGTH, Exercise.MuscleGroup.LEGS,
                Exercise.Equipment.BARBELL, Exercise.DifficultyLevel.INTERMEDIATE, null);
    }

    @Test
    void create_newName_isSaved() {
        when(exerciseRepository.existsByOrganizationIdAndNameIgnoreCaseAndDeletedAtIsNull(organizationId, "Barbell Back Squat")).thenReturn(false);

        Exercise result = service.create(principal(), createRequest("Barbell Back Squat"));

        assertThat(result.getName()).isEqualTo("Barbell Back Squat");
        assertThat(result.getCategory()).isEqualTo(Exercise.Category.STRENGTH);
        assertThat(result.isActive()).isTrue();
        verify(exerciseRepository).save(result);
    }

    @Test
    void create_duplicateNameCaseInsensitive_isRejected() {
        when(exerciseRepository.existsByOrganizationIdAndNameIgnoreCaseAndDeletedAtIsNull(organizationId, "barbell back squat")).thenReturn(true);

        assertThatThrownBy(() -> service.create(principal(), createRequest("barbell back squat")))
                .isInstanceOf(DuplicateResourceException.class);
        verify(exerciseRepository, never()).save(any());
    }

    @Test
    void update_exerciseKeepingItsOwnName_isNotTreatedAsAConflict() {
        UUID exerciseId = UUID.randomUUID();
        Exercise exercise = new Exercise(organizationId, "Barbell Back Squat", Exercise.Category.STRENGTH, Exercise.MuscleGroup.LEGS);
        exercise.setId(exerciseId);
        when(exerciseRepository.findActiveByIdAndOrganizationId(exerciseId, organizationId)).thenReturn(Optional.of(exercise));
        when(exerciseRepository.existsByOrganizationIdAndNameIgnoreCaseAndIdNotAndDeletedAtIsNull(organizationId, "Barbell Back Squat", exerciseId))
                .thenReturn(false);

        UpdateExerciseRequest request = new UpdateExerciseRequest(
                "Barbell Back Squat", "Updated cues", Exercise.Category.STRENGTH, Exercise.MuscleGroup.LEGS, Exercise.Equipment.BARBELL,
                Exercise.DifficultyLevel.ADVANCED, null, true);

        Exercise result = service.update(principal(), exerciseId, request);

        assertThat(result.getDifficultyLevel()).isEqualTo(Exercise.DifficultyLevel.ADVANCED);
    }

    @Test
    void listActive_delegatesToRepository() {
        when(exerciseRepository.findByOrganizationIdAndActiveTrueAndDeletedAtIsNullOrderByNameAsc(organizationId)).thenReturn(List.of());

        List<Exercise> result = service.listActive(principal());

        assertThat(result).isEmpty();
    }
}
