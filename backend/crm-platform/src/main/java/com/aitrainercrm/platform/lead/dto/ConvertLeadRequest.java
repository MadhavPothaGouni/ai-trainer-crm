package com.aitrainercrm.platform.lead.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Every field is optional, with sensible fallbacks in LeadService#convert:
 * <ul>
 *   <li>{@code existingAccountId} set -&gt; link to that account instead of creating one
 *       (validated against the caller's organization). Left null -&gt; a new Account is
 *       created, named {@code newAccountName} if given, else the lead's own
 *       {@code companyName}, else "{full name}'s Account".</li>
 *   <li>{@code createOpportunity} defaults to {@code true} - a lead converting without a
 *       deal to work is the unusual case, not the common one. Set false to convert into
 *       just an Account + Contact.</li>
 * </ul>
 */
public record ConvertLeadRequest(
        UUID existingAccountId,
        @Size(max = 200) String newAccountName,
        Boolean createOpportunity,
        @Size(max = 200) String opportunityName,
        @DecimalMin(value = "0", inclusive = true) BigDecimal opportunityAmount,
        LocalDate opportunityExpectedCloseDate) {

    public boolean shouldCreateOpportunity() {
        return createOpportunity == null || createOpportunity;
    }
}
