package com.aitrainercrm.platform.customfield.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** {@code apiName}/target are immutable after creation (renaming a field's storage key would orphan every existing {@code CustomFieldValue} row) - only label/required/order/active/picklist options can change. */
public record UpdateCustomFieldRequest(
        @NotBlank @Size(max = 150) String label,
        boolean required,
        int displayOrder,
        @NotNull Boolean active,
        List<@Size(max = 100) String> picklistValues) {
}
