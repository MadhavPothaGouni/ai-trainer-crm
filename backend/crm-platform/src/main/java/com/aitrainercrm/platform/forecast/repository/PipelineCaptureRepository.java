package com.aitrainercrm.platform.forecast.repository;

import com.aitrainercrm.platform.forecast.dto.OrgOwnerStageAggregateDto;
import com.aitrainercrm.platform.opportunity.entity.Opportunity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * A second, read-only repository interface over {@link Opportunity} - same "the opportunity
 * module has no reason to know about a downstream module's DTO types" reasoning {@code
 * report.repository.OpportunityAnalyticsRepository} already established. Deliberately not scoped
 * to one organization: {@link #aggregateAllOrganizations} is the one query in {@code forecast/}
 * that spans every tenant on the platform in a single pass, the same genuinely-cross-tenant shape
 * {@code SlaEvaluationService#sweep} uses for the same reason - a nightly job has no single
 * caller's organization to scope itself to.
 */
public interface PipelineCaptureRepository extends JpaRepository<Opportunity, UUID> {

    @Query(
            """
            select new com.aitrainercrm.platform.forecast.dto.OrgOwnerStageAggregateDto(
                o.organizationId, o.ownerId, o.stage, count(o), coalesce(sum(o.amount), 0))
            from Opportunity o
            where o.deletedAt is null
            group by o.organizationId, o.ownerId, o.stage
            """)
    List<OrgOwnerStageAggregateDto> aggregateAllOrganizations();
}
