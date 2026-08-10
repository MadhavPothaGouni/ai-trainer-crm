package com.aitrainercrm.platform.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public record CreateRoleRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description,
        Set<UUID> permissionIds) {
}
