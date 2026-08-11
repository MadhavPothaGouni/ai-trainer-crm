package com.aitrainercrm.platform.customfield.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCustomObjectRequest(
        @NotBlank @Size(max = 80) @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "must be lowercase letters, numbers, and underscores, starting with a letter")
                String apiName,
        @NotBlank @Size(max = 150) String label,
        @NotBlank @Size(max = 150) String pluralLabel,
        @Size(max = 500) String description) {
}
