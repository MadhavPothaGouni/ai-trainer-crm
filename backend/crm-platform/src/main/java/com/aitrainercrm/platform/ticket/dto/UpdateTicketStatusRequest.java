package com.aitrainercrm.platform.ticket.dto;

import com.aitrainercrm.platform.ticket.entity.Ticket;
import jakarta.validation.constraints.NotNull;

public record UpdateTicketStatusRequest(@NotNull Ticket.Status status) {
}
