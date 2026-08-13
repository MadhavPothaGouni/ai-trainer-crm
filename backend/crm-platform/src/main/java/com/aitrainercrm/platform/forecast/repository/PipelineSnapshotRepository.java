package com.aitrainercrm.platform.forecast.repository;

import com.aitrainercrm.platform.forecast.entity.PipelineSnapshot;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PipelineSnapshotRepository extends JpaRepository<PipelineSnapshot, UUID> {

    /**
     * Deletes every organization's rows for one day, letting {@code PipelineSnapshotService#capture} re-insert fresh
     * ones - the delete-then-reinsert idempotency strategy documented in V22's migration comment, rather than a
     * find-or-create per (org, owner, stage) row.
     *
     * <p>Deliberately a bulk {@code @Modifying} JPQL delete rather than a plain derived {@code deleteBy...} method.
     * A derived delete loads the matching rows and queues an {@code EntityManager#remove} per entity, deferred in
     * Hibernate's flush-action queue - and that queue runs pending inserts before pending deletes, regardless of
     * which one was requested first. Since {@code capture()} deletes the day's rows and then immediately persists
     * new ones with the *same* {@code (organization_id, snapshot_date, owner_id, stage)} key inside one
     * transaction, that insert-before-delete ordering would violate {@code uq_pipeline_snapshots_org_date_owner_stage}
     * on every re-capture instead of replacing the old row. A bulk JPQL delete executes immediately via
     * {@code executeUpdate()} against the database, bypassing the action queue entirely, so it's always visible
     * before the subsequent {@code saveAll} flushes its inserts.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PipelineSnapshot s where s.snapshotDate = :snapshotDate")
    int deleteBySnapshotDate(@Param("snapshotDate") LocalDate snapshotDate);

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
