package com.aitrainercrm.platform.checkin.dto;

import com.aitrainercrm.platform.checkin.entity.ClientCheckIn;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Status is deliberately not editable here - see UpdateClientCheckInStatusRequest / PATCH .../status, same reasoning UpdateShiftStatusRequest documents. */
public record UpdateClientCheckInRequest(@NotNull ClientCheckIn.Method method, @Size(max = 2000) String notes) {
}
