package com.aitrainercrm.platform.bodymeasurement.dto;

import com.aitrainercrm.platform.bodymeasurement.entity.BodyMeasurement;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

@Builder
public record BodyMeasurementDto(
        UUID id,
        UUID contactId,
        UUID ownerId,
        LocalDate measuredAt,
        BigDecimal weightValue,
        String weightUnit,
        BigDecimal bodyFatPercent,
        BigDecimal chestCm,
        BigDecimal waistCm,
        BigDecimal hipsCm,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static BodyMeasurementDto from(BodyMeasurement measurement) {
        return BodyMeasurementDto.builder()
                .id(measurement.getId())
                .contactId(measurement.getContactId())
                .ownerId(measurement.getOwnerId())
                .measuredAt(measurement.getMeasuredAt())
                .weightValue(measurement.getWeightValue())
                .weightUnit(measurement.getWeightUnit())
                .bodyFatPercent(measurement.getBodyFatPercent())
                .chestCm(measurement.getChestCm())
                .waistCm(measurement.getWaistCm())
                .hipsCm(measurement.getHipsCm())
                .notes(measurement.getNotes())
                .createdAt(measurement.getCreatedAt())
                .updatedAt(measurement.getUpdatedAt())
                .build();
    }
}
