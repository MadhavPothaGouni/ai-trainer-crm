package com.aitrainercrm.platform.equipment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Status is deliberately not editable here - see UpdateEquipmentReservationStatusRequest / PATCH .../status. */
public record UpdateEquipmentReservationRequest(
        UUID contactId, @NotNull Instant startsAt, @NotNull Instant endsAt, @Size(max = 2000) String notes) {
}
