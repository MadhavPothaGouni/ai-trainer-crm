package com.aitrainercrm.platform.campaign.dto;

import com.aitrainercrm.platform.campaign.entity.Campaign;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

@Builder
public record CampaignDto(
        UUID id,
        String name,
        Campaign.Type type,
        Campaign.Status status,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal budget,
        BigDecimal actualCost,
        String description,
        Instant createdAt,
        Instant updatedAt) {

    public static CampaignDto from(Campaign campaign) {
        return CampaignDto.builder()
                .id(campaign.getId())
                .name(campaign.getName())
                .type(campaign.getType())
                .status(campaign.getStatus())
                .startDate(campaign.getStartDate())
                .endDate(campaign.getEndDate())
                .budget(campaign.getBudget())
                .actualCost(campaign.getActualCost())
                .description(campaign.getDescription())
                .createdAt(campaign.getCreatedAt())
                .updatedAt(campaign.getUpdatedAt())
                .build();
    }
}
