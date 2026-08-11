package com.aitrainercrm.platform.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateTeamRequest(
        @NotBlank @Size(max = 150) String name,

        /** Free text on purpose - see Team's javadoc. Two teams sharing the same string here is exactly what makes them share a DEPARTMENT scope. */
        @Size(max = 100) String department,

        /** Null is fine - a team doesn't need a lead on day one. Non-null must be a real user in the same organization. */
        UUID leadUserId) {
}
