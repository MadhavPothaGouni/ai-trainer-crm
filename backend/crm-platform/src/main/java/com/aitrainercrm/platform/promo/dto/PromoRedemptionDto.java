package com.aitrainercrm.platform.promo.dto;

import com.aitrainercrm.platform.promo.entity.PromoRedemption;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PromoRedemptionDto(
        UUID id,
        UUID promoCodeId,
        UUID contactId,
        UUID ownerId,
        Instant redeemedAt,
        UUID orderId,
        BigDecimal amountDiscounted,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static PromoRedemptionDto from(PromoRedemption redemption) {
        return new PromoRedemptionDto(
                redemption.getId(),
                redemption.getPromoCodeId(),
                redemption.getContactId(),
                redemption.getOwnerId(),
                redemption.getRedeemedAt(),
                redemption.getOrderId(),
                redemption.getAmountDiscounted(),
                redemption.getNotes(),
                redemption.getCreatedAt(),
                redemption.getUpdatedAt());
    }
}
