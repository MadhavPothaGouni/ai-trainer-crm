package com.aitrainercrm.platform.campaign.dto;

import com.aitrainercrm.platform.campaign.entity.CampaignMember;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record CampaignMemberDto(
        UUID id, UUID leadId, UUID contactId, CampaignMember.Status status, Instant respondedAt, Instant createdAt) {

    public static CampaignMemberDto from(CampaignMember member) {
        return CampaignMemberDto.builder()
                .id(member.getId())
                .leadId(member.getLeadId())
                .contactId(member.getContactId())
                .status(member.getStatus())
                .respondedAt(member.getRespondedAt())
                .createdAt(member.getCreatedAt())
                .build();
    }
}
