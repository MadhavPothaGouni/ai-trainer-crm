package com.aitrainercrm.platform.equipment.dto;

import com.aitrainercrm.platform.equipment.entity.MaintenanceLog;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CreateMaintenanceLogRequest(
        @NotNull UUID equipmentId,
        @NotNull Instant performedAt,
        @NotNull MaintenanceLog.Type type,
        @DecimalMin(value = "0", inclusive = true) BigDecimal cost,
        @Size(max = 2000) String notes,
        LocalDate nextDueDate,
        UUID ownerId) {
}
