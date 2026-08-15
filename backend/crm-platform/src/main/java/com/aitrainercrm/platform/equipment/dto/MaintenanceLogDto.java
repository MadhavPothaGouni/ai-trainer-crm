package com.aitrainercrm.platform.equipment.dto;

import com.aitrainercrm.platform.equipment.entity.MaintenanceLog;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MaintenanceLogDto(
        UUID id,
        UUID equipmentId,
        UUID ownerId,
        Instant performedAt,
        MaintenanceLog.Type type,
        BigDecimal cost,
        String notes,
        LocalDate nextDueDate,
        Instant createdAt,
        Instant updatedAt) {

    public static MaintenanceLogDto from(MaintenanceLog log) {
        return new MaintenanceLogDto(
                log.getId(),
                log.getEquipmentId(),
                log.getOwnerId(),
                log.getPerformedAt(),
                log.getType(),
                log.getCost(),
                log.getNotes(),
                log.getNextDueDate(),
                log.getCreatedAt(),
                log.getUpdatedAt());
    }
}
