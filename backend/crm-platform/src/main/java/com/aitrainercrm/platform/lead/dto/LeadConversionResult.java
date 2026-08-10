package com.aitrainercrm.platform.lead.dto;

import java.util.UUID;
import lombok.Builder;

@Builder
public record LeadConversionResult(UUID leadId, UUID accountId, UUID contactId, UUID opportunityId) {
}
