package com.aitrainercrm.platform.commission.dto;

import com.aitrainercrm.platform.commission.entity.CommissionPlan;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record CommissionPlanDto(
        UUID id,
        String name,
        UUID ownerUserId,
        UUID teamId,
        CommissionPlan.RateType rateType,
        BigDecimal rate,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static CommissionPlanDto from(CommissionPlan plan) {
        return CommissionPlanDto.builder()
                .id(plan.getId())
                .name(plan.getName())
                .ownerUserId(plan.getOwnerUserId())
                .teamId(plan.getTeamId())
                .rateType(plan.getRateType())
                .rate(plan.getRate())
                .active(plan.isActive())
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .build();
    }
}
