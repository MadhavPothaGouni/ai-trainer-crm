package com.aitrainercrm.platform.giftcard.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record RedeemGiftCardRequest(@NotNull @DecimalMin(value = "0.01") BigDecimal amount) {
}
