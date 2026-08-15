package com.aitrainercrm.platform.compensation.dto;

import com.aitrainercrm.platform.compensation.entity.CompensationRecord;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CompensationRecordDto(
        UUID id,
        UUID staffUserId,
        UUID ownerId,
        LocalDate payPeriodStart,
        LocalDate payPeriodEnd,
        BigDecimal hoursWorked,
        BigDecimal hourlyRate,
        BigDecimal commissionAmount,
        BigDecimal bonusAmount,
        BigDecimal totalAmount,
        CompensationRecord.Status status,
        Instant paidAt,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static CompensationRecordDto from(CompensationRecord record) {
        return new CompensationRecordDto(
                record.getId(),
                record.getStaffUserId(),
                record.getOwnerId(),
                record.getPayPeriodStart(),
                record.getPayPeriodEnd(),
                record.getHoursWorked(),
                record.getHourlyRate(),
                record.getCommissionAmount(),
                record.getBonusAmount(),
                record.getTotalAmount(),
                record.getStatus(),
                record.getPaidAt(),
                record.getNotes(),
                record.getCreatedAt(),
                record.getUpdatedAt());
    }
}
