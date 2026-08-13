package com.aitrainercrm.platform.certification.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record AwardCertificationRequest(
        @NotNull UUID certificationId,

        /** Null defaults to the caller - see UserCertificationService#resolveHolder, the identical rule CourseEnrollmentService#resolveLearner already applies. */
        UUID userId,

        @NotNull LocalDate earnedAt,
        @Size(max = 100) String credentialNumber) {
}
