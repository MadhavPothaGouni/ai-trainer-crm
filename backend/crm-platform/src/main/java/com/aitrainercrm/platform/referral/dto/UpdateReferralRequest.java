package com.aitrainercrm.platform.referral.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Status is deliberately not editable here - see UpdateReferralStatusRequest / PATCH .../status, same reasoning UpdateClientGoalRequest documents. */
public record UpdateReferralRequest(
        @NotBlank @Size(max = 200) String referredName,
        @Email @Size(max = 255) String referredEmail,
        @Size(max = 50) String referredPhone,
        BigDecimal rewardAmount,
        @Size(max = 2000) String notes) {
}
