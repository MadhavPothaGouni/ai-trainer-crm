package com.aitrainercrm.platform.equipment.dto;

import com.aitrainercrm.platform.equipment.entity.Equipment;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateEquipmentRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 100) String category,
        @Size(max = 100) String serialNumber,
        @Size(max = 200) String location,
        @NotNull Equipment.Status status,
        LocalDate purchaseDate,
        @DecimalMin(value = "0", inclusive = true) BigDecimal purchasePrice,
        @Size(max = 2000) String notes) {
}
