package com.aitrainercrm.platform.equipment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateEquipmentRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 100) String category,
        @Size(max = 100) String serialNumber,
        @Size(max = 200) String location,
        LocalDate purchaseDate,
        @DecimalMin(value = "0", inclusive = true) BigDecimal purchasePrice,
        @Size(max = 2000) String notes) {
}
