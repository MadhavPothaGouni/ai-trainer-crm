package com.aitrainercrm.platform.referral.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateReferralRequest(
        @NotNull UUID referrerContactId,
        @NotBlank @Size(max = 200) String referredName,
        @Email @Size(max = 255) String referredEmail,
        @Size(max = 50) String referredPhone,
        BigDecimal rewardAmount,
        @Size(max = 2000) String notes,

        /** Null defaults to the creator - see AccountService#resolveOwner for the identical rule applied here. */
        UUID ownerId) {
}
