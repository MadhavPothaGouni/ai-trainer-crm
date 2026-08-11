package com.aitrainercrm.platform.ticket.dto;

import com.aitrainercrm.platform.ticket.entity.Ticket;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Status is deliberately not editable here - see UpdateTicketStatusRequest / PATCH .../status, same reasoning UpdateLeadRequest documents. */
public record UpdateTicketRequest(
        @NotBlank @Size(max = 200) String subject,
        @Size(max = 2000) String description,
        @NotNull Ticket.Priority priority,
        UUID accountId,
        UUID contactId) {
}
