package com.aitrainercrm.platform.giftcard.dto;

import com.aitrainercrm.platform.giftcard.entity.GiftCard;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record GiftCardDto(
        UUID id,
        UUID contactId,
        UUID ownerId,
        String code,
        BigDecimal initialBalance,
        BigDecimal currentBalance,
        GiftCard.Status status,
        Instant issuedAt,
        LocalDate expiresAt,
        Instant redeemedAt,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static GiftCardDto from(GiftCard giftCard) {
        return new GiftCardDto(
                giftCard.getId(),
                giftCard.getContactId(),
                giftCard.getOwnerId(),
                giftCard.getCode(),
                giftCard.getInitialBalance(),
                giftCard.getCurrentBalance(),
                giftCard.getStatus(),
                giftCard.getIssuedAt(),
                giftCard.getExpiresAt(),
                giftCard.getRedeemedAt(),
                giftCard.getNotes(),
                giftCard.getCreatedAt(),
                giftCard.getUpdatedAt());
    }
}
