package com.aitrainercrm.platform.trainingsession.dto;

import com.aitrainercrm.platform.trainingsession.entity.TrainingSession;
import com.aitrainercrm.platform.trainingsession.entity.TrainingSessionExercise;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
public record TrainingSessionDto(
        UUID id,
        UUID contactId,
        UUID ownerId,
        UUID bookingSlotId,
        Instant startedAt,
        int durationMinutes,
        TrainingSession.SessionType sessionType,
        TrainingSession.Status status,
        String focusArea,
        Integer clientRpe,
        String coachNotes,
        Instant createdAt,
        Instant updatedAt,
        List<TrainingSessionExerciseDto> exercises) {

    /** Header-only shape, for list endpoints where fetching every session's exercises would be wasteful - see QuoteDto's identical split. */
    public static TrainingSessionDto from(TrainingSession session) {
        return from(session, List.of());
    }

    public static TrainingSessionDto from(TrainingSession session, List<TrainingSessionExercise> exercises) {
        return TrainingSessionDto.builder()
                .id(session.getId())
                .contactId(session.getContactId())
                .ownerId(session.getOwnerId())
                .bookingSlotId(session.getBookingSlotId())
                .startedAt(session.getStartedAt())
                .durationMinutes(session.getDurationMinutes())
                .sessionType(session.getSessionType())
                .status(session.getStatus())
                .focusArea(session.getFocusArea())
                .clientRpe(session.getClientRpe())
                .coachNotes(session.getCoachNotes())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .exercises(exercises.stream().map(TrainingSessionExerciseDto::from).toList())
                .build();
    }
}
