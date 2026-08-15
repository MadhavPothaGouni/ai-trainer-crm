package com.aitrainercrm.platform.equipment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record CreateEquipmentReservationRequest(
        @NotNull UUID equipmentId,
        UUID contactId,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @Size(max = 2000) String notes,

        /** Null defaults to the creator - see AccountService#resolveOwner for the identical rule applied here. */
        UUID ownerId) {
}
