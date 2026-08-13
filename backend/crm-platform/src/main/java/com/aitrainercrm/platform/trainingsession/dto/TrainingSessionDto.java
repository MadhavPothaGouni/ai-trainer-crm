package com.aitrainercrm.platform.trainingsession.dto;

import com.aitrainercrm.platform.trainingsession.entity.TrainingSession;
import java.time.Instant;
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
        Instant updatedAt) {

    public static TrainingSessionDto from(TrainingSession session) {
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
                .build();
    }
}
