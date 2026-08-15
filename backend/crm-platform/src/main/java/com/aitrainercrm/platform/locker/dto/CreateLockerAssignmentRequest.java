package com.aitrainercrm.platform.locker.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record CreateLockerAssignmentRequest(
        @NotNull UUID lockerId,
        @NotNull UUID contactId,
        LocalDate expiresAt,
        @Size(max = 2000) String notes,

        /** Null defaults to the creator - see AccountService#resolveOwner for the identical rule applied here. */
        UUID ownerId) {
}
