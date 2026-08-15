package com.aitrainercrm.platform.membership.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateMembershipRequest(
        LocalDate endDate,
        LocalDate nextBillingDate,
        boolean autoRenew,
        Integer remainingCredits,
        @Size(max = 2000) String notes) {
}
