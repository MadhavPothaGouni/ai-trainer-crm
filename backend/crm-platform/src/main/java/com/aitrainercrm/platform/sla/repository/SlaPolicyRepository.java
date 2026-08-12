package com.aitrainercrm.platform.sla.repository;

import com.aitrainercrm.platform.sla.entity.SlaPolicy;
import com.aitrainercrm.platform.ticket.entity.Ticket;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, UUID> {

    Optional<SlaPolicy> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Page<SlaPolicy> findByOrganizationIdOrderByPriorityAscNameAsc(UUID organizationId, Pageable pageable);

    /** The lookup SlaEvaluationService#evaluate runs for every ticket - "is there a live policy for this org+priority right now." Relies on uq_sla_policies_org_priority_active (V20) to guarantee at most one row ever matches. */
    Optional<SlaPolicy> findByOrganizationIdAndPriorityAndActiveTrue(UUID organizationId, Ticket.Priority priority);

    /** Backs SlaPolicyService's pre-check for the same uniqueness rule, so a conflict comes back as a clean 409 instead of a raw DB constraint violation - excludes the policy being edited (see #update) so re-saving an already-active policy with unchanged priority doesn't collide with itself. */
    boolean existsByOrganizationIdAndPriorityAndActiveTrueAndIdNot(UUID organizationId, Ticket.Priority priority, UUID excludedId);

    boolean existsByOrganizationIdAndPriorityAndActiveTrue(UUID organizationId, Ticket.Priority priority);
}
