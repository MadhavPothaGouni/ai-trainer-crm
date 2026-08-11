package com.aitrainercrm.platform.workflow.repository;

import com.aitrainercrm.platform.workflow.entity.Workflow;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowRepository extends JpaRepository<Workflow, UUID> {

    Optional<Workflow> findByIdAndOrganizationIdAndDeletedAtIsNull(UUID id, UUID organizationId);

    Page<Workflow> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    Page<Workflow> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(UUID organizationId, Set<UUID> ownerIds, Pageable pageable);

    /** What {@code WorkflowEngineListener} queries on every matching CRM event - see V11's partial index backing this exact shape. */
    List<Workflow> findByOrganizationIdAndTriggerResourceAndTriggerEventAndActiveTrueAndDeletedAtIsNull(
            UUID organizationId, Workflow.TriggerResource triggerResource, Workflow.TriggerEvent triggerEvent);
}
