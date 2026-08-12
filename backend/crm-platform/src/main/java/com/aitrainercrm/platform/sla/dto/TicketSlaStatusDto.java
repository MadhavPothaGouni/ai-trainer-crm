package com.aitrainercrm.platform.sla.dto;

import com.aitrainercrm.platform.sla.entity.TicketSlaStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record TicketSlaStatusDto(
        UUID ticketId,
        UUID slaPolicyId,
        Instant responseDueAt,
        Instant resolutionDueAt,
        boolean responseBreached,
        boolean resolutionBreached,
        Instant responseBreachedAt,
        Instant resolutionBreachedAt,
        boolean escalated,
        Instant escalatedAt) {

    public static TicketSlaStatusDto from(TicketSlaStatus status) {
        return TicketSlaStatusDto.builder()
                .ticketId(status.getTicketId())
                .slaPolicyId(status.getSlaPolicyId())
                .responseDueAt(status.getResponseDueAt())
                .resolutionDueAt(status.getResolutionDueAt())
                .responseBreached(status.isResponseBreached())
                .resolutionBreached(status.isResolutionBreached())
                .responseBreachedAt(status.getResponseBreachedAt())
                .resolutionBreachedAt(status.getResolutionBreachedAt())
                .escalated(status.isEscalated())
                .escalatedAt(status.getEscalatedAt())
                .build();
    }
}
