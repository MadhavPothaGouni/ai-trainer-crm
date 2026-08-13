package com.aitrainercrm.platform.course.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record CreateCourseEnrollmentRequest(
        @NotNull UUID courseId,

        /** Null defaults to the caller (self-enrollment) - see CourseEnrollmentService#resolveLearner for the identical rule TicketService#resolveOwner already applies elsewhere. */
        UUID userId,

        LocalDate dueDate) {
}
