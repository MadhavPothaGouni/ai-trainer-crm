package com.aitrainercrm.platform.customfield.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateCustomObjectRequest(
        @NotBlank @Size(max = 150) String label,
        @NotBlank @Size(max = 150) String pluralLabel,
        @Size(max = 500) String description,
        @NotNull Boolean active) {
}
