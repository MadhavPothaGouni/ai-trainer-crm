package com.aitrainercrm.platform.trainingsession.dto;

import com.aitrainercrm.platform.trainingsession.entity.TrainingSession;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record CreateTrainingSessionRequest(
        @NotNull UUID contactId,

        /** Null is fine - plenty of sessions get logged without ever having gone through the booking flow. Non-null must exist in the same organization. See TrainingSession's javadoc. */
        UUID bookingSlotId,

        @NotNull Instant startedAt,
        @NotNull @Min(1) @Max(600) Integer durationMinutes,
        @NotNull TrainingSession.SessionType sessionType,
        @Size(max = 200) String focusArea,
        @Min(1) @Max(10) Integer clientRpe,
        @Size(max = 2000) String coachNotes,

        /** Null defaults to the creator - see AccountService#resolveOwner for the identical rule applied here. */
        UUID ownerId) {
}
