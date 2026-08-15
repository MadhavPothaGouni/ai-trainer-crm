package com.aitrainercrm.platform.equipment.dto;

import com.aitrainercrm.platform.equipment.entity.EquipmentReservation;
import java.time.Instant;
import java.util.UUID;

public record EquipmentReservationDto(
        UUID id,
        UUID equipmentId,
        UUID contactId,
        UUID ownerId,
        Instant startsAt,
        Instant endsAt,
        EquipmentReservation.Status status,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static EquipmentReservationDto from(EquipmentReservation reservation) {
        return new EquipmentReservationDto(
                reservation.getId(),
                reservation.getEquipmentId(),
                reservation.getContactId(),
                reservation.getOwnerId(),
                reservation.getStartsAt(),
                reservation.getEndsAt(),
                reservation.getStatus(),
                reservation.getNotes(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt());
    }
}
