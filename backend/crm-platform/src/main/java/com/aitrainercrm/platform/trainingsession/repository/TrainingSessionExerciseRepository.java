package com.aitrainercrm.platform.trainingsession.repository;

import com.aitrainercrm.platform.trainingsession.entity.TrainingSessionExercise;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingSessionExerciseRepository extends JpaRepository<TrainingSessionExercise, UUID> {

    List<TrainingSessionExercise> findByTrainingSessionIdOrderBySequenceOrderAsc(UUID trainingSessionId);

    Optional<TrainingSessionExercise> findByIdAndTrainingSessionId(UUID id, UUID trainingSessionId);

    long countByTrainingSessionId(UUID trainingSessionId);
}
