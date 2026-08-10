package com.aitrainercrm.platform.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Self-service profile fields only - status, roles, and organization are admin-only (see UpdateUserRolesRequest/UpdateUserStatusRequest). */
public record UpdateProfileRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Size(max = 30) String phone,
        @Size(max = 60) String timezone,
        @Size(max = 20) String locale) {
}
