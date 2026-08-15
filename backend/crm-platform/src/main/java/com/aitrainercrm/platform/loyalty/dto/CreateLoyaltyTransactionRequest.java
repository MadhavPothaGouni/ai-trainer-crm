package com.aitrainercrm.platform.loyalty.dto;

import com.aitrainercrm.platform.loyalty.entity.LoyaltyTransaction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateLoyaltyTransactionRequest(
        @NotNull UUID contactId,
        @NotNull Integer points,
        @NotNull LoyaltyTransaction.Reason reason,
        @Size(max = 2000) String notes,

        /** Null defaults to the creator - see AccountService#resolveOwner for the identical rule applied here. */
        UUID ownerId) {
}
