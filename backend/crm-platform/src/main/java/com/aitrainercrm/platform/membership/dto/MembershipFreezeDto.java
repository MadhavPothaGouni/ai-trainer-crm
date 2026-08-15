package com.aitrainercrm.platform.membership.dto;

import com.aitrainercrm.platform.membership.entity.MembershipFreeze;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MembershipFreezeDto(
        UUID id,
        UUID membershipId,
        UUID ownerId,
        LocalDate freezeStart,
        LocalDate freezeEnd,
        String reason,
        MembershipFreeze.Status status,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static MembershipFreezeDto from(MembershipFreeze freeze) {
        return new MembershipFreezeDto(
                freeze.getId(),
                freeze.getMembershipId(),
                freeze.getOwnerId(),
                freeze.getFreezeStart(),
                freeze.getFreezeEnd(),
                freeze.getReason(),
                freeze.getStatus(),
                freeze.getNotes(),
                freeze.getCreatedAt(),
                freeze.getUpdatedAt());
    }
}
