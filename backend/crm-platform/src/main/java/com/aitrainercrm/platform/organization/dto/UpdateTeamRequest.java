package com.aitrainercrm.platform.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpdateTeamRequest(
        @NotBlank @Size(max = 150) String name, @Size(max = 100) String department, UUID leadUserId, UUID regionId) {
}
