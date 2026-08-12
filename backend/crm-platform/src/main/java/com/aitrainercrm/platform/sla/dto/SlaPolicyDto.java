package com.aitrainercrm.platform.sla.dto;

import com.aitrainercrm.platform.sla.entity.SlaPolicy;
import com.aitrainercrm.platform.ticket.entity.Ticket;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record SlaPolicyDto(
        UUID id,
        String name,
        Ticket.Priority priority,
        int responseTargetMinutes,
        int resolutionTargetMinutes,
        UUID escalateToUserId,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static SlaPolicyDto from(SlaPolicy policy) {
        return SlaPolicyDto.builder()
                .id(policy.getId())
                .name(policy.getName())
                .priority(policy.getPriority())
                .responseTargetMinutes(policy.getResponseTargetMinutes())
                .resolutionTargetMinutes(policy.getResolutionTargetMinutes())
                .escalateToUserId(policy.getEscalateToUserId())
                .active(policy.isActive())
                .createdAt(policy.getCreatedAt())
                .updatedAt(policy.getUpdatedAt())
                .build();
    }
}
