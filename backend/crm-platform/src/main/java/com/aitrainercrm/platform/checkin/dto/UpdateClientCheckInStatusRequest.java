package com.aitrainercrm.platform.checkin.dto;

import com.aitrainercrm.platform.checkin.entity.ClientCheckIn;
import jakarta.validation.constraints.NotNull;

public record UpdateClientCheckInStatusRequest(@NotNull ClientCheckIn.Status status) {
}
