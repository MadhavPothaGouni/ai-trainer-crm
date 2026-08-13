package com.aitrainercrm.platform.trainingsession.dto;

import com.aitrainercrm.platform.trainingsession.entity.TrainingSession;
import jakarta.validation.constraints.NotNull;

public record UpdateTrainingSessionStatusRequest(@NotNull TrainingSession.Status status) {
}
