package com.aitrainercrm.platform.campaign.repository;

import com.aitrainercrm.platform.campaign.dto.CampaignMemberStatusCountDto;
import com.aitrainercrm.platform.campaign.entity.CampaignMember;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampaignMemberRepository extends JpaRepository<CampaignMember, UUID> {

    List<CampaignMember> findByCampaignIdOrderByCreatedAtAsc(UUID campaignId);

    Optional<CampaignMember> findByIdAndCampaignId(UUID id, UUID campaignId);

    boolean existsByCampaignIdAndLeadId(UUID campaignId, UUID leadId);

    boolean existsByCampaignIdAndContactId(UUID campaignId, UUID contactId);

    @Query("""
            select new com.aitrainercrm.platform.campaign.dto.CampaignMemberStatusCountDto(m.status, count(m))
            from CampaignMember m
            where m.campaignId = :campaignId
            group by m.status
            """)
    List<CampaignMemberStatusCountDto> countByStatus(@Param("campaignId") UUID campaignId);
}
