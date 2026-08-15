package com.aitrainercrm.platform.groupclass.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record UpdateClassSessionRequest(
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @Min(0) Integer capacityOverride,
        @Size(max = 2000) String notes) {
}
