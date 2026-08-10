package com.aitrainercrm.platform.opportunity.dto;

import com.aitrainercrm.platform.opportunity.entity.Opportunity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

@Builder
public record OpportunityDto(
        UUID id,
        UUID accountId,
        UUID primaryContactId,
        String name,
        Opportunity.Stage stage,
        BigDecimal amount,
        String currency,
        LocalDate expectedCloseDate,
        LocalDate actualCloseDate,
        String description,
        UUID ownerId,
        Instant createdAt,
        Instant updatedAt) {

    public static OpportunityDto from(Opportunity opportunity) {
        return OpportunityDto.builder()
                .id(opportunity.getId())
                .accountId(opportunity.getAccountId())
                .primaryContactId(opportunity.getPrimaryContactId())
                .name(opportunity.getName())
                .stage(opportunity.getStage())
                .amount(opportunity.getAmount())
                .currency(opportunity.getCurrency())
                .expectedCloseDate(opportunity.getExpectedCloseDate())
                .actualCloseDate(opportunity.getActualCloseDate())
                .description(opportunity.getDescription())
                .ownerId(opportunity.getOwnerId())
                .createdAt(opportunity.getCreatedAt())
                .updatedAt(opportunity.getUpdatedAt())
                .build();
    }
}
