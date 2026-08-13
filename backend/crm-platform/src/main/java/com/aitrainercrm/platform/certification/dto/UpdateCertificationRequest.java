package com.aitrainercrm.platform.certification.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCertificationRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 200) String issuingBody,
        @Size(max = 2000) String description,
        @Min(1) Integer validityMonths,
        boolean active) {
}
