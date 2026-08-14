package com.aitrainercrm.platform.bodymeasurement.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** No status field to omit here, unlike UpdateContractRequest/UpdateNutritionPlanRequest - see BodyMeasurement's javadoc for why this module has no status/lifecycle concept at all. */
public record UpdateBodyMeasurementRequest(
        @NotNull LocalDate measuredAt,
        @DecimalMin("0") BigDecimal weightValue,
        @Size(max = 10) String weightUnit,
        @DecimalMin("0") BigDecimal bodyFatPercent,
        @DecimalMin("0") BigDecimal chestCm,
        @DecimalMin("0") BigDecimal waistCm,
        @DecimalMin("0") BigDecimal hipsCm,
        @Size(max = 2000) String notes) {
}
