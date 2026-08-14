package com.aitrainercrm.platform.bodymeasurement.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateBodyMeasurementRequest(
        @NotNull UUID contactId,
        @NotNull LocalDate measuredAt,
        @DecimalMin("0") BigDecimal weightValue,
        @Size(max = 10) String weightUnit,
        @DecimalMin("0") BigDecimal bodyFatPercent,
        @DecimalMin("0") BigDecimal chestCm,
        @DecimalMin("0") BigDecimal waistCm,
        @DecimalMin("0") BigDecimal hipsCm,
        @Size(max = 2000) String notes,

        /** Null defaults to the creator - see AccountService#resolveOwner for the identical rule applied here. */
        UUID ownerId) {
}
