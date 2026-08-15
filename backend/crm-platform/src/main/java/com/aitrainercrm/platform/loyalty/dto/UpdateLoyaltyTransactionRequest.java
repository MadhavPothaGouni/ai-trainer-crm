package com.aitrainercrm.platform.loyalty.dto;

import com.aitrainercrm.platform.loyalty.entity.LoyaltyTransaction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateLoyaltyTransactionRequest(
        @NotNull Integer points,
        @NotNull LoyaltyTransaction.Reason reason,
        @Size(max = 2000) String notes) {
}
