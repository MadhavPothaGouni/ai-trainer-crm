package com.aitrainercrm.platform.clientgoal.dto;

import com.aitrainercrm.platform.clientgoal.entity.ClientGoal;
import jakarta.validation.constraints.NotNull;

public record UpdateClientGoalStatusRequest(@NotNull ClientGoal.Status status) {
}
