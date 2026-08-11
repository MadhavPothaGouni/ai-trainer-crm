package com.aitrainercrm.platform.ticket.dto;

import com.aitrainercrm.platform.ticket.entity.Ticket;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record TicketDto(
        UUID id,
        UUID accountId,
        UUID contactId,
        String subject,
        String description,
        Ticket.Status status,
        Ticket.Priority priority,
        UUID ownerId,
        Instant resolvedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static TicketDto from(Ticket ticket) {
        return TicketDto.builder()
                .id(ticket.getId())
                .accountId(ticket.getAccountId())
                .contactId(ticket.getContactId())
                .subject(ticket.getSubject())
                .description(ticket.getDescription())
                .status(ticket.getStatus())
                .priority(ticket.getPriority())
                .ownerId(ticket.getOwnerId())
                .resolvedAt(ticket.getResolvedAt())
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .build();
    }
}
