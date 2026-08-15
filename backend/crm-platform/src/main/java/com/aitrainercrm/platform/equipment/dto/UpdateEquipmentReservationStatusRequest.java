package com.aitrainercrm.platform.equipment.dto;

import com.aitrainercrm.platform.equipment.entity.EquipmentReservation;
import jakarta.validation.constraints.NotNull;

public record UpdateEquipmentReservationStatusRequest(@NotNull EquipmentReservation.Status status) {
}
