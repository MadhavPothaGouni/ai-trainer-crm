package com.aitrainercrm.platform.exercise.dto;

import com.aitrainercrm.platform.exercise.entity.PersonalRecord;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PersonalRecordDto(
        UUID id,
        UUID contactId,
        UUID exerciseId,
        UUID ownerId,
        PersonalRecord.RecordType recordType,
        BigDecimal value,
        Instant achievedAt,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static PersonalRecordDto from(PersonalRecord record) {
        return new PersonalRecordDto(
                record.getId(),
                record.getContactId(),
                record.getExerciseId(),
                record.getOwnerId(),
                record.getRecordType(),
                record.getValue(),
                record.getAchievedAt(),
                record.getNotes(),
                record.getCreatedAt(),
                record.getUpdatedAt());
    }
}
