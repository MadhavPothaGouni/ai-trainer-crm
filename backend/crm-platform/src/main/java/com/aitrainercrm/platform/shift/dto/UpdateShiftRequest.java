package com.aitrainercrm.platform.shift.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;

public record UpdateShiftRequest(@NotNull LocalDate shiftDate, @NotNull Instant startsAt, @NotNull Instant endsAt, @Size(max = 2000) String notes) {
}
