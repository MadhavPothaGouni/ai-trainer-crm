package com.aitrainercrm.platform.noshow.dto;

import com.aitrainercrm.platform.noshow.entity.NoShowRecord;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record NoShowRecordDto(
        UUID id,
        UUID contactId,
        UUID ownerId,
        Instant occurredAt,
        NoShowRecord.RelatedType relatedType,
        BigDecimal feeAmount,
        boolean waived,
        Instant waivedAt,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static NoShowRecordDto from(NoShowRecord record) {
        return new NoShowRecordDto(
                record.getId(),
                record.getContactId(),
                record.getOwnerId(),
                record.getOccurredAt(),
                record.getRelatedType(),
                record.getFeeAmount(),
                record.isWaived(),
                record.getWaivedAt(),
                record.getNotes(),
                record.getCreatedAt(),
                record.getUpdatedAt());
    }
}
