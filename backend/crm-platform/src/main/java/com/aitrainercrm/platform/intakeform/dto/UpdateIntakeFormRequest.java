package com.aitrainercrm.platform.intakeform.dto;

import com.aitrainercrm.platform.intakeform.entity.IntakeForm;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateIntakeFormRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull IntakeForm.FormType formType,
        boolean active,
        @Size(max = 2000) String notes) {
}
