package com.aitrainercrm.platform.sla.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** priority is deliberately not editable here - see SlaPolicyService#update's javadoc for why changing a policy's priority is modeled as retire-and-recreate rather than an in-place edit. */
public record UpdateSlaPolicyRequest(
        @NotBlank @Size(max = 150) String name,
        @Positive int responseTargetMinutes,
        @Positive int resolutionTargetMinutes,
        UUID escalateToUserId,
        boolean active) {
}
