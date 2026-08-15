package com.aitrainercrm.platform.referral.dto;

import com.aitrainercrm.platform.referral.entity.Referral;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record ReferralDto(
        UUID id,
        UUID referrerContactId,
        String referredName,
        String referredEmail,
        String referredPhone,
        UUID ownerId,
        Referral.Status status,
        UUID convertedContactId,
        BigDecimal rewardAmount,
        Instant rewardIssuedAt,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static ReferralDto from(Referral referral) {
        return ReferralDto.builder()
                .id(referral.getId())
                .referrerContactId(referral.getReferrerContactId())
                .referredName(referral.getReferredName())
                .referredEmail(referral.getReferredEmail())
                .referredPhone(referral.getReferredPhone())
                .ownerId(referral.getOwnerId())
                .status(referral.getStatus())
                .convertedContactId(referral.getConvertedContactId())
                .rewardAmount(referral.getRewardAmount())
                .rewardIssuedAt(referral.getRewardIssuedAt())
                .notes(referral.getNotes())
                .createdAt(referral.getCreatedAt())
                .updatedAt(referral.getUpdatedAt())
                .build();
    }
}
