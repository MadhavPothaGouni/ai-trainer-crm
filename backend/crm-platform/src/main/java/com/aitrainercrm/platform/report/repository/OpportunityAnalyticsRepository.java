package com.aitrainercrm.platform.report.repository;

import com.aitrainercrm.platform.opportunity.entity.Opportunity;
import com.aitrainercrm.platform.report.dto.OwnerStageAggregateDto;
import com.aitrainercrm.platform.report.dto.PipelineStageSummaryDto;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Read-only aggregation queries over {@link Opportunity} for the Reporting
 * module. Deliberately a second repository interface for the same entity
 * rather than adding these methods onto {@code opportunity.repository.OpportunityRepository}
 * - the opportunity module has no reason to know about report/dto types,
 * and Spring Data is happy to proxy more than one repository interface
 * against the same table.
 *
 * <p>{@code ownerIds} is nullable in both queries: {@code null} means "no
 * filter, see the whole organization" (ReportService passes this when
 * {@code ScopeAuthorizationService#visibleOwnerIds} returns an empty
 * {@code Optional}), matching the same "empty means unrestricted" contract
 * every other scope-filtered list query in this codebase follows.
 */
public interface OpportunityAnalyticsRepository extends JpaRepository<Opportunity, UUID> {

    @Query(
            """
            select new com.aitrainercrm.platform.report.dto.PipelineStageSummaryDto(o.stage, count(o), coalesce(sum(o.amount), 0))
            from Opportunity o
            where o.organizationId = :organizationId and o.deletedAt is null and (:ownerIds is null or o.ownerId in :ownerIds)
            group by o.stage
            """)
    List<PipelineStageSummaryDto> summarizeByStage(@Param("organizationId") UUID organizationId, @Param("ownerIds") Set<UUID> ownerIds);

    @Query(
            """
            select new com.aitrainercrm.platform.report.dto.OwnerStageAggregateDto(o.ownerId, o.stage, count(o), coalesce(sum(o.amount), 0))
            from Opportunity o
            where o.organizationId = :organizationId and o.deletedAt is null and (:ownerIds is null or o.ownerId in :ownerIds)
            group by o.ownerId, o.stage
            """)
    List<OwnerStageAggregateDto> aggregateByOwnerAndStage(@Param("organizationId") UUID organizationId, @Param("ownerIds") Set<UUID> ownerIds);
}
