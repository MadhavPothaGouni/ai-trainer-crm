package com.aitrainercrm.platform.campaign.dto;

import com.aitrainercrm.platform.campaign.entity.CampaignMember;
import jakarta.validation.constraints.NotNull;

public record UpdateCampaignMemberStatusRequest(@NotNull CampaignMember.Status status) {
}
