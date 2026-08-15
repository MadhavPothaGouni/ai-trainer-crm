package com.aitrainercrm.platform.groupclass.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateClassWaitlistRequest(
        @NotNull UUID classSessionId,
        @NotNull UUID contactId,
        @Size(max = 2000) String notes,

        /** Null defaults to the creator - see AccountService#resolveOwner for the identical rule applied here. */
        UUID ownerId) {
}
