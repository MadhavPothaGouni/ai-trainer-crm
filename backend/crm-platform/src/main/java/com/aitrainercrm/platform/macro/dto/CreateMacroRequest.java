package com.aitrainercrm.platform.macro.dto;

import com.aitrainercrm.platform.ticket.entity.Ticket;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateMacroRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 2000) String body,

        /** Null means applying this macro never changes the ticket's status - see Macro's javadoc. */
        Ticket.Status newStatus) {
}
