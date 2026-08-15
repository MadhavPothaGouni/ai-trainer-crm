package com.aitrainercrm.platform.promo.dto;

import com.aitrainercrm.platform.promo.entity.PromoCode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdatePromoCodeRequest(
        @NotBlank @Size(max = 50) String code,
        @Size(max = 500) String description,
        @NotNull PromoCode.DiscountType discountType,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal discountValue,
        @Min(1) Integer maxRedemptions,
        boolean active,
        LocalDate expiresAt,
        @Size(max = 2000) String notes) {
}
