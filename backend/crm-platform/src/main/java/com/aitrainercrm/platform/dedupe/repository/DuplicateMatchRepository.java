package com.aitrainercrm.platform.dedupe.repository;

import com.aitrainercrm.platform.dedupe.entity.DuplicateMatch;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DuplicateMatchRepository extends JpaRepository<DuplicateMatch, UUID> {

    @Query("select m from DuplicateMatch m where m.id = :id and m.organizationId = :organizationId")
    Optional<DuplicateMatch> findByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    /** DuplicateDetectionListener's own existence check before flagging a pair - see V23's migration comment for why record_a_id/record_b_id must already be normalized (order-independent) before calling this. */
    Optional<DuplicateMatch> findByOrganizationIdAndEntityTypeAndRecordAIdAndRecordBId(
            UUID organizationId, DuplicateMatch.EntityType entityType, UUID recordAId, UUID recordBId);

    /** The review-queue lookup - unpaginated, same "a queue like this is realistically small" reasoning ApprovalRequestController#myApprovalTasks already applies. */
    List<DuplicateMatch> findByOrganizationIdAndEntityTypeAndStatusOrderByCreatedAtAsc(
            UUID organizationId, DuplicateMatch.EntityType entityType, DuplicateMatch.Status status);
}
