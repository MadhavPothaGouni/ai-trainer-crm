package com.aitrainercrm.platform.certification.dto;

import com.aitrainercrm.platform.certification.entity.UserCertification;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

@Builder
public record UserCertificationDto(
        UUID id,
        UUID certificationId,
        UUID userId,
        String credentialNumber,
        LocalDate earnedAt,
        LocalDate expiresAt,
        UserCertification.Status status,
        boolean expired,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static UserCertificationDto from(UserCertification userCertification) {
        return UserCertificationDto.builder()
                .id(userCertification.getId())
                .certificationId(userCertification.getCertificationId())
                .userId(userCertification.getUserId())
                .credentialNumber(userCertification.getCredentialNumber())
                .earnedAt(userCertification.getEarnedAt())
                .expiresAt(userCertification.getExpiresAt())
                .status(userCertification.getStatus())
                .expired(userCertification.isExpired())
                .notes(userCertification.getNotes())
                .createdAt(userCertification.getCreatedAt())
                .updatedAt(userCertification.getUpdatedAt())
                .build();
    }
}
