package com.aitrainercrm.platform.intakeform.dto;

import jakarta.validation.constraints.Size;

public record UpdateIntakeFormSubmissionRequest(
        @Size(max = 20000) String responses,
        @Size(max = 2000) String notes) {
}
