package com.aitrainercrm.platform.intakeform.dto;

import com.aitrainercrm.platform.intakeform.entity.IntakeForm;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateIntakeFormRequest(
        @NotBlank @Size(max = 200) String title,

        /** Null defaults to OTHER - see IntakeFormService#create. */
        IntakeForm.FormType formType,
        @Size(max = 2000) String notes) {
}
