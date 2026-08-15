package com.aitrainercrm.platform.timeoff.dto;

import com.aitrainercrm.platform.timeoff.entity.TimeOffRequest;
import jakarta.validation.constraints.NotNull;

public record UpdateTimeOffRequestStatusRequest(@NotNull TimeOffRequest.Status status) {
}
