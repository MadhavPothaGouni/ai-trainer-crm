package com.aitrainercrm.platform.promo.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record UpdatePromoRedemptionRequest(
        UUID orderId, @DecimalMin(value = "0", inclusive = true) BigDecimal amountDiscounted, @Size(max = 2000) String notes) {
}
