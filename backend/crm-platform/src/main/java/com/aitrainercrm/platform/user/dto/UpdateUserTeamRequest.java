package com.aitrainercrm.platform.user.dto;

import java.util.UUID;

/** Unlike UpdateUserRolesRequest/UpdateUserStatusRequest, null is a legitimate value here - it unassigns the user from whatever team they're on rather than being rejected as missing input. */
public record UpdateUserTeamRequest(UUID teamId) {
}
