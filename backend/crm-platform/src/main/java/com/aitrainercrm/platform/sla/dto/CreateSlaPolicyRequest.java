package com.aitrainercrm.platform.sla.dto;

import com.aitrainercrm.platform.ticket.entity.Ticket;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** active isn't a field here - a newly created policy is always active=true; use UpdateSlaPolicyRequest to retire one later. escalateToUserId is optional - see SlaPolicy's javadoc. */
public record CreateSlaPolicyRequest(
        @NotBlank @Size(max = 150) String name,
        @NotNull Ticket.Priority priority,
        @Positive int responseTargetMinutes,
        @Positive int resolutionTargetMinutes,
        UUID escalateToUserId) {
}
