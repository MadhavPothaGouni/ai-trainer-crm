package com.aitrainercrm.platform.lead.dto;

import com.aitrainercrm.platform.lead.entity.Lead;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record LeadDto(
        UUID id,
        String firstName,
        String lastName,
        String fullName,
        String email,
        String phone,
        String companyName,
        String title,
        Lead.Status status,
        Lead.Source source,
        String description,
        UUID ownerId,
        UUID convertedAccountId,
        UUID convertedContactId,
        UUID convertedOpportunityId,
        Instant convertedAt,
        int score,
        Instant createdAt,
        Instant updatedAt) {

    public static LeadDto from(Lead lead) {
        return LeadDto.builder()
                .id(lead.getId())
                .firstName(lead.getFirstName())
                .lastName(lead.getLastName())
                .fullName(lead.getFullName())
                .email(lead.getEmail())
                .phone(lead.getPhone())
                .companyName(lead.getCompanyName())
                .title(lead.getTitle())
                .status(lead.getStatus())
                .source(lead.getSource())
                .description(lead.getDescription())
                .ownerId(lead.getOwnerId())
                .convertedAccountId(lead.getConvertedAccountId())
                .convertedContactId(lead.getConvertedContactId())
                .convertedOpportunityId(lead.getConvertedOpportunityId())
                .convertedAt(lead.getConvertedAt())
                .score(lead.getScore())
                .createdAt(lead.getCreatedAt())
                .updatedAt(lead.getUpdatedAt())
                .build();
    }
}
