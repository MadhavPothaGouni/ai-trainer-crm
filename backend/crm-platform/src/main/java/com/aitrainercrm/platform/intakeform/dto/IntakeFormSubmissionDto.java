package com.aitrainercrm.platform.intakeform.dto;

import com.aitrainercrm.platform.intakeform.entity.IntakeFormSubmission;
import java.time.Instant;
import java.util.UUID;

public record IntakeFormSubmissionDto(
        UUID id,
        UUID formId,
        UUID contactId,
        UUID ownerId,
        Instant submittedAt,
        String responses,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static IntakeFormSubmissionDto from(IntakeFormSubmission submission) {
        return new IntakeFormSubmissionDto(
                submission.getId(),
                submission.getFormId(),
                submission.getContactId(),
                submission.getOwnerId(),
                submission.getSubmittedAt(),
                submission.getResponses(),
                submission.getNotes(),
                submission.getCreatedAt(),
                submission.getUpdatedAt());
    }
}
