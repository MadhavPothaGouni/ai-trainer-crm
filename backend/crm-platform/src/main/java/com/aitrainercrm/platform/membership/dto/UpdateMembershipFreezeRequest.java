package com.aitrainercrm.platform.membership.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateMembershipFreezeRequest(
        @NotNull LocalDate freezeStart, @NotNull LocalDate freezeEnd, @Size(max = 500) String reason, @Size(max = 2000) String notes) {
}
