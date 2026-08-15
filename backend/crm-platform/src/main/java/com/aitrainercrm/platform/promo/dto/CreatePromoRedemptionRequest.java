package com.aitrainercrm.platform.promo.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record CreatePromoRedemptionRequest(
        @NotNull UUID promoCodeId,
        @NotNull UUID contactId,
        UUID orderId,
        @DecimalMin(value = "0", inclusive = true) BigDecimal amountDiscounted,
        @Size(max = 2000) String notes,

        /** Null defaults to the creator - see AccountService#resolveOwner for the identical rule applied here. */
        UUID ownerId) {
}
