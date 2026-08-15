package com.aitrainercrm.platform.intakeform.dto;

import com.aitrainercrm.platform.intakeform.entity.IntakeForm;
import java.time.Instant;
import java.util.UUID;

public record IntakeFormDto(
        UUID id,
        String title,
        IntakeForm.FormType formType,
        boolean active,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static IntakeFormDto from(IntakeForm form) {
        return new IntakeFormDto(form.getId(), form.getTitle(), form.getFormType(), form.isActive(), form.getNotes(), form.getCreatedAt(), form.getUpdatedAt());
    }
}
