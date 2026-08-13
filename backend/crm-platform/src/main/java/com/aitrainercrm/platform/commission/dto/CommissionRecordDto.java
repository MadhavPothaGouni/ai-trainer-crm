package com.aitrainercrm.platform.commission.dto;

import com.aitrainercrm.platform.commission.entity.CommissionPlan;
import com.aitrainercrm.platform.commission.entity.CommissionRecord;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record CommissionRecordDto(
        UUID id,
        UUID opportunityId,
        UUID ownerUserId,
        UUID planId,
        BigDecimal dealAmount,
        CommissionPlan.RateType rateType,
        BigDecimal rate,
        BigDecimal commissionAmount,
        CommissionRecord.Status status,
        Instant earnedAt,
        Instant paidAt) {

    public static CommissionRecordDto from(CommissionRecord record) {
        return CommissionRecordDto.builder()
                .id(record.getId())
                .opportunityId(record.getOpportunityId())
                .ownerUserId(record.getOwnerUserId())
                .planId(record.getPlanId())
                .dealAmount(record.getDealAmount())
                .rateType(record.getRateType())
                .rate(record.getRate())
                .commissionAmount(record.getCommissionAmount())
                .status(record.getStatus())
                .earnedAt(record.getEarnedAt())
                .paidAt(record.getPaidAt())
                .build();
    }
}
