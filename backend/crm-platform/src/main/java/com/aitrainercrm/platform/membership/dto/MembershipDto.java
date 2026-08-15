package com.aitrainercrm.platform.membership.dto;

import com.aitrainercrm.platform.membership.entity.Membership;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

@Builder
public record MembershipDto(
        UUID id,
        UUID contactId,
        UUID membershipPlanId,
        UUID ownerId,
        Membership.Status status,
        BigDecimal billingCyclePrice,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate nextBillingDate,
        boolean autoRenew,
        Integer remainingCredits,
        Instant pausedAt,
        Instant cancelledAt,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static MembershipDto from(Membership membership) {
        return MembershipDto.builder()
                .id(membership.getId())
                .contactId(membership.getContactId())
                .membershipPlanId(membership.getMembershipPlanId())
                .ownerId(membership.getOwnerId())
                .status(membership.getStatus())
                .billingCyclePrice(membership.getBillingCyclePrice())
                .startDate(membership.getStartDate())
                .endDate(membership.getEndDate())
                .nextBillingDate(membership.getNextBillingDate())
                .autoRenew(membership.isAutoRenew())
                .remainingCredits(membership.getRemainingCredits())
                .pausedAt(membership.getPausedAt())
                .cancelledAt(membership.getCancelledAt())
                .notes(membership.getNotes())
                .createdAt(membership.getCreatedAt())
                .updatedAt(membership.getUpdatedAt())
                .build();
    }
}
