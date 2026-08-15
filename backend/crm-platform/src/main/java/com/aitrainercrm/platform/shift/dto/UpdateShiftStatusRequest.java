package com.aitrainercrm.platform.shift.dto;

import com.aitrainercrm.platform.shift.entity.Shift;
import jakarta.validation.constraints.NotNull;

public record UpdateShiftStatusRequest(@NotNull Shift.Status status) {
}
