package com.aitrainercrm.platform.shift.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CreateShiftRequest(
        UUID shiftTemplateId,
        @NotNull LocalDate shiftDate,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @Size(max = 2000) String notes,
        UUID ownerId) {
}
