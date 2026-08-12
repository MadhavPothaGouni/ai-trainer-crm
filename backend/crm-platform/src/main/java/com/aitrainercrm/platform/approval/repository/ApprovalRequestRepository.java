package com.aitrainercrm.platform.approval.repository;

import com.aitrainercrm.platform.approval.entity.ApprovalRequest;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> {

    /** No deletedAt filter, unlike every owner-scoped module before this one - see ApprovalRequest's javadoc for why there's nothing to soft-delete around. */
    @Query("select r from ApprovalRequest r where r.id = :id and r.organizationId = :organizationId")
    Optional<ApprovalRequest> findByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<ApprovalRequest> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    Page<ApprovalRequest> findByOrganizationIdAndRequestedByUserIdInOrderByCreatedAtDesc(UUID organizationId, Set<UUID> requestedByUserIds, Pageable pageable);
}
