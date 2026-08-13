package com.aitrainercrm.platform.certification.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCertificationRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 200) String issuingBody,
        @Size(max = 2000) String description,

        /** Null means this credential never expires - see Certification#validityMonths' javadoc. */
        @Min(1) Integer validityMonths) {
}
