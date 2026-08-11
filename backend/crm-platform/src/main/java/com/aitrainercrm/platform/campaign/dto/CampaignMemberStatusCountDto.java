package com.aitrainercrm.platform.campaign.dto;

import com.aitrainercrm.platform.campaign.entity.CampaignMember;

/** Row shape for {@code CampaignMemberRepository#countByStatus}'s JPQL constructor expression - one row per status actually present among a campaign's members. */
public record CampaignMemberStatusCountDto(CampaignMember.Status status, long count) {
}
