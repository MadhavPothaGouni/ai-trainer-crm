package com.aitrainercrm.platform.membership.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record CreateMembershipRequest(
        @NotNull UUID contactId,
        @NotNull UUID membershipPlanId,
        @NotNull LocalDate startDate,
        LocalDate nextBillingDate,
        boolean autoRenew,
        @Size(max = 2000) String notes,

        /** Null defaults to the creator - see AccountService#resolveOwner for the identical rule applied here. */
        UUID ownerId) {
}
