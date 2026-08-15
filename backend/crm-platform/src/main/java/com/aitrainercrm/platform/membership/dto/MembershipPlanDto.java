package com.aitrainercrm.platform.membership.dto;

import com.aitrainercrm.platform.membership.entity.MembershipPlan;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record MembershipPlanDto(
        UUID id,
        String name,
        String description,
        MembershipPlan.BillingCycle billingCycle,
        BigDecimal price,
        String currency,
        Integer sessionCredits,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static MembershipPlanDto from(MembershipPlan plan) {
        return MembershipPlanDto.builder()
                .id(plan.getId())
                .name(plan.getName())
                .description(plan.getDescription())
                .billingCycle(plan.getBillingCycle())
                .price(plan.getPrice())
                .currency(plan.getCurrency())
                .sessionCredits(plan.getSessionCredits())
                .active(plan.isActive())
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .build();
    }
}
