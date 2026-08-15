package com.aitrainercrm.platform.exercise.dto;

import com.aitrainercrm.platform.exercise.entity.PersonalRecord;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreatePersonalRecordRequest(
        @NotNull UUID contactId,
        @NotNull UUID exerciseId,
        @NotNull PersonalRecord.RecordType recordType,
        @NotNull @Positive BigDecimal value,
        Instant achievedAt,
        @Size(max = 2000) String notes,

        /** Null defaults to the creator - see AccountService#resolveOwner for the identical rule applied here. */
        UUID ownerId) {
}
