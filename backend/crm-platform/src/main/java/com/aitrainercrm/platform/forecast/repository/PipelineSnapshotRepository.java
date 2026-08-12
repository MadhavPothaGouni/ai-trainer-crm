package com.aitrainercrm.platform.forecast.repository;

import com.aitrainercrm.platform.forecast.entity.PipelineSnapshot;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PipelineSnapshotRepository extends JpaRepository<PipelineSnapshot, UUID> {

    /** Deletes every organization's rows for one day, letting {@code PipelineSnapshotService#capture} re-insert fresh ones - the delete-then-reinsert idempotency strategy documented in V22's migration comment, rather than a find-or-create per (org, owner, stage) row. */
    long deleteBySnapshotDate(LocalDate snapshotDate);

    /** {@code ownerIds} is nullable, same "null means no filter" contract every other scope-filtered query in this codebase follows (see {@code OpportunityAnalyticsRepository}'s javadoc) - {@code PipelineSnapshotService} passes {@code null} when {@code ScopeAuthorizationService#visibleOwnerIds} returns an empty {@code Optional} (ORGANIZATION-scope access). */
    @Query(
            """
            select s from PipelineSnapshot s
            where s.organizationId = :organizationId
              and s.snapshotDate between :from and :to
              and (:ownerIds is null or s.ownerId in :ownerIds)
            order by s.snapshotDate asc
            """)
    List<PipelineSnapshot> findVisible(
            @Param("organizationId") UUID organizationId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("ownerIds") Set<UUID> ownerIds);
}
