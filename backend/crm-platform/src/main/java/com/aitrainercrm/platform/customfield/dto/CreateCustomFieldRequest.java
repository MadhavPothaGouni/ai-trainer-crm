package com.aitrainercrm.platform.customfield.dto;

import com.aitrainercrm.platform.customfield.entity.CustomField;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Exactly one of {@code standardEntityType}/{@code customObjectId} must be set - validated in {@code CustomFieldService}, not here (jakarta-validation can't express cross-field XOR cleanly without a custom annotation). */
public record CreateCustomFieldRequest(
        CustomField.StandardEntityType standardEntityType,
        UUID customObjectId,
        @NotBlank @Size(max = 80) @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "must be lowercase letters, numbers, and underscores, starting with a letter")
                String apiName,
        @NotBlank @Size(max = 150) String label,
        @NotNull CustomField.FieldType fieldType,
        boolean required,
        int displayOrder,
        List<@Size(max = 100) String> picklistValues) {
}
