package com.aitrainercrm.platform.campaign.dto;

import com.aitrainercrm.platform.campaign.entity.CampaignMember;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;

/** GET /campaigns/{id}/stats - total membership plus a per-status breakdown, zero-filled for every status so the frontend never has to guess a missing key means zero. */
@Builder
public record CampaignStatsDto(UUID campaignId, long totalMembers, Map<CampaignMember.Status, Long> countsByStatus) {

    public static CampaignStatsDto from(UUID campaignId, List<CampaignMemberStatusCountDto> rows) {
        Map<CampaignMember.Status, Long> counts = new EnumMap<>(CampaignMember.Status.class);
        for (CampaignMember.Status status : CampaignMember.Status.values()) {
            counts.put(status, 0L);
        }
        long total = 0;
        for (CampaignMemberStatusCountDto row : rows) {
            counts.put(row.status(), row.count());
            total += row.count();
        }
        return CampaignStatsDto.builder().campaignId(campaignId).totalMembers(total).countsByStatus(counts).build();
    }
}
