package com.aitrainercrm.platform.exercise.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.DuplicateResourceException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.exercise.dto.CreateExerciseRequest;
import com.aitrainercrm.platform.exercise.dto.UpdateExerciseRequest;
import com.aitrainercrm.platform.exercise.entity.Exercise;
import com.aitrainercrm.platform.exercise.repository.ExerciseRepository;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The exercise (movement) catalog. No {@link com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService}
 * calls here - see {@link Exercise}'s javadoc and {@code CourseService}'s identical reasoning:
 * Exercise has no {@code ownerId}, so the controller's {@code @PreAuthorize} (any of TEAM/
 * DEPARTMENT/ORGANIZATION) is the whole authorization story for this service.
 */
@Service
@RequiredArgsConstructor
public class ExerciseService {

    private final ExerciseRepository exerciseRepository;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<Exercise> list(UserPrincipal principal, Pageable pageable) {
        return exerciseRepository.findByOrganizationIdAndDeletedAtIsNullOrderByNameAsc(principal.getOrganizationId(), pageable);
    }

    /** Unpaginated - a session-logging form needs the full active catalog at once, same reasoning CourseService#listActive already gives. */
    @Transactional(readOnly = true)
    public List<Exercise> listActive(UserPrincipal principal) {
        return exerciseRepository.findByOrganizationIdAndActiveTrueAndDeletedAtIsNullOrderByNameAsc(principal.getOrganizationId());
    }

    @Transactional(readOnly = true)
    public Exercise get(UserPrincipal principal, UUID exerciseId) {
        return findOrThrow(principal.getOrganizationId(), exerciseId);
    }

    @Transactional
    public Exercise create(UserPrincipal principal, CreateExerciseRequest request) {
        assertNameAvailable(principal.getOrganizationId(), request.name(), null);

        Exercise exercise = new Exercise(principal.getOrganizationId(), request.name(), request.category(), request.primaryMuscleGroup());
        applyFields(exercise, request.description(), request.equipment(), request.difficultyLevel(), request.videoUrl());
        exerciseRepository.save(exercise);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "Exercise", exercise.getId()));
        return exercise;
    }

    @Transactional
    public Exercise update(UserPrincipal principal, UUID exerciseId, UpdateExerciseRequest request) {
        Exercise exercise = findOrThrow(principal.getOrganizationId(), exerciseId);
        assertNameAvailable(principal.getOrganizationId(), request.name(), exerciseId);

        exercise.setName(request.name());
        exercise.setCategory(request.category());
        exercise.setPrimaryMuscleGroup(request.primaryMuscleGroup());
        exercise.setActive(request.active());
        applyFields(exercise, request.description(), request.equipment(), request.difficultyLevel(), request.videoUrl());
        exerciseRepository.save(exercise);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Exercise", exercise.getId()));
        return exercise;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID exerciseId) {
        Exercise exercise = findOrThrow(principal.getOrganizationId(), exerciseId);
        exercise.setDeletedAt(Instant.now());
        exerciseRepository.save(exercise);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "Exercise", exerciseId));
    }

    Exercise findOrThrow(UUID organizationId, UUID exerciseId) {
        return exerciseRepository.findActiveByIdAndOrganizationId(exerciseId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Exercise", exerciseId));
    }

    private void assertNameAvailable(UUID organizationId, String name, UUID excludingExerciseId) {
        boolean inUse = excludingExerciseId == null
                ? exerciseRepository.existsByOrganizationIdAndNameIgnoreCaseAndDeletedAtIsNull(organizationId, name)
                : exerciseRepository.existsByOrganizationIdAndNameIgnoreCaseAndIdNotAndDeletedAtIsNull(organizationId, name, excludingExerciseId);
        if (inUse) {
            throw new DuplicateResourceException("An exercise with this name already exists");
        }
    }

    private void applyFields(
            Exercise exercise, String description, Exercise.Equipment equipment, Exercise.DifficultyLevel difficultyLevel, String videoUrl) {
        exercise.setDescription(description);
        exercise.setEquipment(equipment);
        exercise.setDifficultyLevel(difficultyLevel);
        exercise.setVideoUrl(videoUrl);
    }
}
