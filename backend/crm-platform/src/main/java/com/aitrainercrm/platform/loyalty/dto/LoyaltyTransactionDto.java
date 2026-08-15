package com.aitrainercrm.platform.loyalty.dto;

import com.aitrainercrm.platform.loyalty.entity.LoyaltyTransaction;
import java.time.Instant;
import java.util.UUID;

public record LoyaltyTransactionDto(
        UUID id,
        UUID contactId,
        UUID ownerId,
        int points,
        LoyaltyTransaction.Reason reason,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static LoyaltyTransactionDto from(LoyaltyTransaction transaction) {
        return new LoyaltyTransactionDto(
                transaction.getId(),
                transaction.getContactId(),
                transaction.getOwnerId(),
                transaction.getPoints(),
                transaction.getReason(),
                transaction.getNotes(),
                transaction.getCreatedAt(),
                transaction.getUpdatedAt());
    }
}
