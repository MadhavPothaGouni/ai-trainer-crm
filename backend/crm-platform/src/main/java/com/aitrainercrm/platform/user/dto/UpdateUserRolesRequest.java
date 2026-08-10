package com.aitrainercrm.platform.user.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.Set;
import java.util.UUID;

/** A full replace, not a diff - the request is the complete new set of roles for the target user. */
public record UpdateUserRolesRequest(@NotEmpty Set<UUID> roleIds) {
}
