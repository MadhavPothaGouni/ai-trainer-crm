package com.aitrainercrm.platform.campaign.dto;

import com.aitrainercrm.platform.campaign.entity.Campaign;
import jakarta.validation.constraints.NotNull;

public record UpdateCampaignStatusRequest(@NotNull Campaign.Status status) {
}
