package com.aitrainercrm.platform.promo.dto;

import com.aitrainercrm.platform.promo.entity.PromoCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PromoCodeDto(
        UUID id,
        String code,
        String description,
        PromoCode.DiscountType discountType,
        BigDecimal discountValue,
        Integer maxRedemptions,
        boolean active,
        LocalDate expiresAt,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static PromoCodeDto from(PromoCode promoCode) {
        return new PromoCodeDto(
                promoCode.getId(),
                promoCode.getCode(),
                promoCode.getDescription(),
                promoCode.getDiscountType(),
                promoCode.getDiscountValue(),
                promoCode.getMaxRedemptions(),
                promoCode.isActive(),
                promoCode.getExpiresAt(),
                promoCode.getNotes(),
                promoCode.getCreatedAt(),
                promoCode.getUpdatedAt());
    }
}
