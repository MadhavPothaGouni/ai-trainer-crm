package com.aitrainercrm.platform.sequence.dto;

import com.aitrainercrm.platform.sequence.entity.SequenceEnrollment;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateSequenceEnrollmentRequest(
        @NotNull UUID sequenceId,
        @NotNull SequenceEnrollment.TargetType targetType,
        @NotNull UUID targetId,

        /** Null defaults to the caller - see SequenceEnrollmentService#resolveOwner's javadoc, the identical rule TicketService#resolveOwner already applies. */
        UUID ownerId) {
}
