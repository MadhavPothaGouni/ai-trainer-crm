package com.aitrainercrm.platform.membership.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record CreateMembershipFreezeRequest(
        @NotNull UUID membershipId,
        @NotNull LocalDate freezeStart,
        @NotNull LocalDate freezeEnd,
        @Size(max = 500) String reason,
        @Size(max = 2000) String notes,

        /** Null defaults to the creator - see AccountService#resolveOwner for the identical rule applied here. */
        UUID ownerId) {
}
