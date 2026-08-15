package com.aitrainercrm.platform.checkin.dto;

import com.aitrainercrm.platform.checkin.entity.ClientCheckIn;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateClientCheckInRequest(
        @NotNull UUID contactId,
        @NotNull ClientCheckIn.Method method,
        @Size(max = 2000) String notes,

        /** Null defaults to the creator - see AccountService#resolveOwner for the identical rule applied here. */
        UUID ownerId) {
}
