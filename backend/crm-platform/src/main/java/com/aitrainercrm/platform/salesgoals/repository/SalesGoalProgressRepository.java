package com.aitrainercrm.platform.salesgoals.repository;

import com.aitrainercrm.platform.opportunity.entity.Opportunity;
import com.aitrainercrm.platform.salesgoals.dto.WonTotalsDto;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Read-only aggregation over {@link Opportunity} for the Sales Goals module - a second repository
 * interface over the same table, the same "the opportunity module has no reason to know about
 * this" reasoning {@code report.repository.OpportunityAnalyticsRepository}'s javadoc documents.
 *
 * <p>Unlike that repository's {@code ownerIds}, which is nullable and means "no filter, see the
 * whole organization," {@code ownerIds} here is always a concrete (possibly empty) set - a goal's
 * progress is only ever measured against a specific person or a specific team's current members,
 * never the whole org, so there's no "unrestricted" case to support. {@code stage} is passed as a
 * bound parameter rather than an inline JPQL enum literal, the same "sidestep JPQL enum-literal
 * syntax" reasoning {@code LeadRepository#findDuplicateCandidatesByEmail}'s javadoc documents -
 * every caller passes {@code Opportunity.Stage.CLOSED_WON}.
 */
public interface SalesGoalProgressRepository extends JpaRepository<Opportunity, UUID> {

    @Query(
            """
            select new com.aitrainercrm.platform.salesgoals.dto.WonTotalsDto(count(o), coalesce(sum(o.amount), 0))
            from Opportunity o
            where o.organizationId = :organizationId and o.deletedAt is null and o.stage = :stage
                and o.ownerId in :ownerIds and o.actualCloseDate between :periodStart and :periodEnd
            """)
    WonTotalsDto sumWonBetween(
            @Param("organizationId") UUID organizationId, @Param("ownerIds") Set<UUID> ownerIds, @Param("stage") Opportunity.Stage stage,
            @Param("periodStart") LocalDate periodStart, @Param("periodEnd") LocalDate periodEnd);
}
