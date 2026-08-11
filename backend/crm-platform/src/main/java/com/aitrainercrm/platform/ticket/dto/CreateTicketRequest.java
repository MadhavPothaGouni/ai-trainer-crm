package com.aitrainercrm.platform.ticket.dto;

import com.aitrainercrm.platform.ticket.entity.Ticket;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateTicketRequest(
        @NotBlank @Size(max = 200) String subject,
        @Size(max = 2000) String description,
        @NotNull Ticket.Priority priority,

        /** Null is fine - a ticket doesn't have to be tied to a tracked Account or Contact. Non-null must exist in the same organization. */
        UUID accountId,
        UUID contactId,

        /** Null defaults to the creator - see AccountService#resolveOwner for the identical rule applied here. */
        UUID ownerId) {
}
