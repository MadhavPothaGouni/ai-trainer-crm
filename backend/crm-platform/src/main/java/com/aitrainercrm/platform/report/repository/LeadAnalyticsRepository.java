package com.aitrainercrm.platform.report.repository;

import com.aitrainercrm.platform.lead.entity.Lead;
import com.aitrainercrm.platform.report.dto.LeadFunnelStageDto;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Read-only aggregation query over {@link Lead} for the lead conversion funnel report - see OpportunityAnalyticsRepository's javadoc for why this is a separate repository interface from lead.repository.LeadRepository. */
public interface LeadAnalyticsRepository extends JpaRepository<Lead, UUID> {

    @Query(
            """
            select new com.aitrainercrm.platform.report.dto.LeadFunnelStageDto(l.status, count(l))
            from Lead l
            where l.organizationId = :organizationId and l.deletedAt is null and (:ownerIds is null or l.ownerId in :ownerIds)
            group by l.status
            """)
    List<LeadFunnelStageDto> summarizeByStatus(@Param("organizationId") UUID organizationId, @Param("ownerIds") Set<UUID> ownerIds);
}
