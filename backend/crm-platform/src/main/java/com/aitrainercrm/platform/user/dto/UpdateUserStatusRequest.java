package com.aitrainercrm.platform.user.dto;

import com.aitrainercrm.platform.user.entity.User;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(@NotNull User.Status status) {
}
