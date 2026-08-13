package com.aitrainercrm.platform.sequence.dto;

import com.aitrainercrm.platform.sequence.entity.SequenceEnrollment;
import jakarta.validation.constraints.NotNull;

/** Only ACTIVE/PAUSED/CANCELLED are ever set through this endpoint - COMPLETED is only ever reached automatically, by SequenceEnrollmentService#advance walking off the end of the step list. See that method's javadoc. */
public record UpdateSequenceEnrollmentStatusRequest(@NotNull SequenceEnrollment.Status status) {
}
