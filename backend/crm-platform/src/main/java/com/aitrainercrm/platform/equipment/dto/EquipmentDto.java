package com.aitrainercrm.platform.equipment.dto;

import com.aitrainercrm.platform.equipment.entity.Equipment;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record EquipmentDto(
        UUID id,
        String name,
        String category,
        String serialNumber,
        String location,
        Equipment.Status status,
        LocalDate purchaseDate,
        BigDecimal purchasePrice,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static EquipmentDto from(Equipment equipment) {
        return new EquipmentDto(
                equipment.getId(),
                equipment.getName(),
                equipment.getCategory(),
                equipment.getSerialNumber(),
                equipment.getLocation(),
                equipment.getStatus(),
                equipment.getPurchaseDate(),
                equipment.getPurchasePrice(),
                equipment.getNotes(),
                equipment.getCreatedAt(),
                equipment.getUpdatedAt());
    }
}
