package com.aitrainercrm.platform.timeoff.dto;

import com.aitrainercrm.platform.timeoff.entity.TimeOffRequest;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record CreateTimeOffRequestRequest(
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull TimeOffRequest.Type type,
        @Size(max = 2000) String reason,
        @Size(max = 2000) String notes,

        /** Null defaults to the creator - see AccountService#resolveOwner for the identical rule applied here. */
        UUID ownerId) {
}
