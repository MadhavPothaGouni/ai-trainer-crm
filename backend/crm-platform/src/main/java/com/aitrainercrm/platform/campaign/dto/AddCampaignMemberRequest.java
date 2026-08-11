package com.aitrainercrm.platform.campaign.dto;

import java.util.UUID;

/** Exactly one of leadId/contactId must be set - validated by CampaignService#addMember (and, as a last line of defense, the DB check constraint in V9). */
public record AddCampaignMemberRequest(UUID leadId, UUID contactId) {
}
