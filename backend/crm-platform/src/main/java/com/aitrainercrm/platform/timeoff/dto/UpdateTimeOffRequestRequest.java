package com.aitrainercrm.platform.timeoff.dto;

import com.aitrainercrm.platform.timeoff.entity.TimeOffRequest;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Status is deliberately not editable here - see UpdateTimeOffRequestStatusRequest / PATCH .../status, same reasoning UpdateReferralRequest documents. */
public record UpdateTimeOffRequestRequest(
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull TimeOffRequest.Type type,
        @Size(max = 2000) String reason,
        @Size(max = 2000) String notes) {
}
