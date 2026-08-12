package com.aitrainercrm.platform.sla.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.sla.dto.CreateSlaPolicyRequest;
import com.aitrainercrm.platform.sla.dto.UpdateSlaPolicyRequest;
import com.aitrainercrm.platform.sla.entity.SlaPolicy;
import com.aitrainercrm.platform.sla.repository.SlaPolicyRepository;
import com.aitrainercrm.platform.ticket.entity.Ticket;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD for {@link SlaPolicy} definitions - entirely gated by {@code SLA_POLICY:*:ORGANIZATION}
 * (no {@code ScopeAuthorizationService} call anywhere here, same as {@code CustomFieldService}),
 * plus the one invariant the database's partial unique index (V20's {@code
 * uq_sla_policies_org_priority_active}) also enforces as a backstop: at most one ACTIVE policy
 * per (organization, priority). Pre-checking it here means a conflict comes back as a clean 409
 * with a real message instead of a raw {@code DataIntegrityViolationException} turning into a
 * 500.
 */
@Service
@RequiredArgsConstructor
public class SlaPolicyService {

    private final SlaPolicyRepository slaPolicyRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<SlaPolicy> list(UserPrincipal principal, Pageable pageable) {
        return slaPolicyRepository.findByOrganizationIdOrderByPriorityAscNameAsc(principal.getOrganizationId(), pageable);
    }

    @Transactional(readOnly = true)
    public SlaPolicy get(UserPrincipal principal, UUID policyId) {
        return findOrThrow(principal.getOrganizationId(), policyId);
    }

    @Transactional
    public SlaPolicy create(UserPrincipal principal, CreateSlaPolicyRequest request) {
        UUID organizationId = principal.getOrganizationId();
        assertUserInOrganization(organizationId, request.escalateToUserId());
        if (slaPolicyRepository.existsByOrganizationIdAndPriorityAndActiveTrue(organizationId, request.priority())) {
            throw activePolicyConflict(request.priority());
        }

        SlaPolicy policy = new SlaPolicy(
                organizationId, request.name(), request.priority(), request.responseTargetMinutes(), request.resolutionTargetMinutes());
        policy.setEscalateToUserId(request.escalateToUserId());
        slaPolicyRepository.save(policy);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), organizationId, "SlaPolicy", policy.getId()));
        return policy;
    }

    /** priority is not editable - see UpdateSlaPolicyRequest's javadoc. Reactivating a previously-retired policy still has to pass the same one-active-per-priority check as creating a new one. */
    @Transactional
    public SlaPolicy update(UserPrincipal principal, UUID policyId, UpdateSlaPolicyRequest request) {
        UUID organizationId = principal.getOrganizationId();
        SlaPolicy policy = findOrThrow(organizationId, policyId);
        assertUserInOrganization(organizationId, request.escalateToUserId());
        if (request.active() && slaPolicyRepository.existsByOrganizationIdAndPriorityAndActiveTrueAndIdNot(organizationId, policy.getPriority(), policyId)) {
            throw activePolicyConflict(policy.getPriority());
        }

        policy.setName(request.name());
        policy.setResponseTargetMinutes(request.responseTargetMinutes());
        policy.setResolutionTargetMinutes(request.resolutionTargetMinutes());
        policy.setEscalateToUserId(request.escalateToUserId());
        policy.setActive(request.active());
        slaPolicyRepository.save(policy);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), organizationId, "SlaPolicy", policy.getId()));
        return policy;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID policyId) {
        SlaPolicy policy = findOrThrow(principal.getOrganizationId(), policyId);
        slaPolicyRepository.delete(policy);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "SlaPolicy", policyId));
    }

    private BusinessException activePolicyConflict(Ticket.Priority priority) {
        return new BusinessException(
                "SLA_POLICY_ALREADY_ACTIVE",
                "There's already an active SLA policy for %s priority - deactivate it first".formatted(priority),
                HttpStatus.CONFLICT);
    }

    private SlaPolicy findOrThrow(UUID organizationId, UUID policyId) {
        return slaPolicyRepository.findByIdAndOrganizationId(policyId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("SlaPolicy", policyId));
    }

    private void assertUserInOrganization(UUID organizationId, UUID userId) {
        if (userId == null) return;
        boolean exists = userRepository.findActiveById(userId).map(u -> organizationId.equals(u.getOrganizationId())).orElse(false);
        if (!exists) {
            throw new ResourceNotFoundException("User", userId);
        }
    }
}
