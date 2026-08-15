package com.aitrainercrm.platform.room.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Status is deliberately not editable here - see UpdateRoomBookingStatusRequest / PATCH .../status, same reasoning UpdateLockerAssignmentRequest documents. */
public record UpdateRoomBookingRequest(
        @NotBlank @Size(max = 200) String purpose,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @Size(max = 2000) String notes) {
}
