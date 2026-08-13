package com.aitrainercrm.platform.certification.dto;

import com.aitrainercrm.platform.certification.entity.Certification;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record CertificationDto(
        UUID id,
        String name,
        String issuingBody,
        String description,
        Integer validityMonths,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static CertificationDto from(Certification certification) {
        return CertificationDto.builder()
                .id(certification.getId())
                .name(certification.getName())
                .issuingBody(certification.getIssuingBody())
                .description(certification.getDescription())
                .validityMonths(certification.getValidityMonths())
                .active(certification.isActive())
                .createdAt(certification.getCreatedAt())
                .updatedAt(certification.getUpdatedAt())
                .build();
    }
}
