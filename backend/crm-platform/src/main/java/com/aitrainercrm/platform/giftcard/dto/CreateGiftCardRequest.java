package com.aitrainercrm.platform.giftcard.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateGiftCardRequest(
        @NotNull UUID contactId,
        @NotBlank @Size(max = 50) String code,
        @NotNull @DecimalMin(value = "0.01") BigDecimal initialBalance,
        LocalDate expiresAt,
        @Size(max = 2000) String notes,

        /** Null defaults to the creator - see AccountService#resolveOwner for the identical rule applied here. */
        UUID ownerId) {
}
