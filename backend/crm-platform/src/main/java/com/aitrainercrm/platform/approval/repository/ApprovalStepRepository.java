package com.aitrainercrm.platform.approval.repository;

import com.aitrainercrm.platform.approval.entity.ApprovalStep;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalStepRepository extends JpaRepository<ApprovalStep, UUID> {

    List<ApprovalStep> findByApprovalRequestIdOrderByStepNumberAsc(UUID approvalRequestId);

    Optional<ApprovalStep> findByApprovalRequestIdAndStepNumber(UUID approvalRequestId, int stepNumber);

    /** Backs the visibility carve-out ApprovalRequestService#get applies on top of the normal owner-scope ladder - see ApprovalRequest's javadoc. */
    boolean existsByApprovalRequestIdAndApproverUserId(UUID approvalRequestId, UUID approverUserId);

    /** The "my approvals" inbox - every step assigned to me, not just the ones currently actionable (see ApprovalStepDto#actionable for why a caller still needs to know about steps that aren't next in line yet). */
    Page<ApprovalStep> findByOrganizationIdAndApproverUserIdAndStatusOrderByCreatedAtAsc(
            UUID organizationId, UUID approverUserId, ApprovalStep.Status status, Pageable pageable);
}
