package com.aitrainercrm.platform.groupclass.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record CreateClassSessionRequest(
        @NotNull UUID groupClassId,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @Min(0) Integer capacityOverride,
        @Size(max = 2000) String notes,
        UUID ownerId) {
}
