package com.aitrainercrm.platform.lead.dto;

import com.aitrainercrm.platform.lead.entity.Lead;
import jakarta.validation.constraints.NotNull;

/** For NEW/CONTACTED/QUALIFIED/UNQUALIFIED only - CONVERTED is reachable exclusively through POST .../convert, since it isn't just a field flip (see LeadService#convert). */
public record UpdateLeadStatusRequest(@NotNull Lead.Status status) {
}
