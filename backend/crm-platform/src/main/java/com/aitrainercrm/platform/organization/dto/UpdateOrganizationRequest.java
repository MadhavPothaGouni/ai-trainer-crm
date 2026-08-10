package com.aitrainercrm.platform.organization.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateOrganizationRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(min = 3, max = 3) String defaultCurrency,
        @Size(max = 60) String timezone,
        @Min(1) @Max(12) int fiscalYearStartMonth) {
}
