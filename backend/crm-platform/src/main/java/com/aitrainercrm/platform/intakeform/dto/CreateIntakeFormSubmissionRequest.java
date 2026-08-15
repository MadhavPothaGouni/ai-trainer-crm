package com.aitrainercrm.platform.intakeform.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateIntakeFormSubmissionRequest(
        @NotNull UUID formId,
        @NotNull UUID contactId,
        @Size(max = 20000) String responses,
        @Size(max = 2000) String notes,

        /** Null defaults to the creator - see AccountService#resolveOwner for the identical rule applied here. */
        UUID ownerId) {
}
