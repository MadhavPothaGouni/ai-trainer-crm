package com.aitrainercrm.platform.membership.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.membership.dto.CreateMembershipPlanRequest;
import com.aitrainercrm.platform.membership.dto.UpdateMembershipPlanRequest;
import com.aitrainercrm.platform.membership.entity.MembershipPlan;
import com.aitrainercrm.platform.membership.repository.MembershipPlanRepository;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The membership-plan catalog. Exactly {@link com.aitrainercrm.platform.product.service.ProductService}'s
 * shape - no {@link com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService}
 * calls here, since a plan has no {@code ownerId} (see the entity's javadoc); the controller's
 * {@code @PreAuthorize} (any of TEAM/DEPARTMENT/ORGANIZATION) is the whole authorization story.
 */
@Service
@RequiredArgsConstructor
public class MembershipPlanService {

    private final MembershipPlanRepository membershipPlanRepository;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<MembershipPlan> list(UserPrincipal principal, Pageable pageable) {
        return membershipPlanRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable);
    }

    @Transactional(readOnly = true)
    public MembershipPlan get(UserPrincipal principal, UUID membershipPlanId) {
        return findOrThrow(principal.getOrganizationId(), membershipPlanId);
    }

    @Transactional
    public MembershipPlan create(UserPrincipal principal, CreateMembershipPlanRequest request) {
        MembershipPlan plan = new MembershipPlan(principal.getOrganizationId(), request.name());
        applyFields(plan, request.description(), request.billingCycle(), request.price(), request.currency(), request.sessionCredits());
        membershipPlanRepository.save(plan);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "MembershipPlan", plan.getId()));
        return plan;
    }

    @Transactional
    public MembershipPlan update(UserPrincipal principal, UUID membershipPlanId, UpdateMembershipPlanRequest request) {
        MembershipPlan plan = findOrThrow(principal.getOrganizationId(), membershipPlanId);
        plan.setName(request.name());
        plan.setActive(request.active());
        applyFields(plan, request.description(), request.billingCycle(), request.price(), request.currency(), request.sessionCredits());
        membershipPlanRepository.save(plan);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "MembershipPlan", plan.getId()));
        return plan;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID membershipPlanId) {
        MembershipPlan plan = findOrThrow(principal.getOrganizationId(), membershipPlanId);
        plan.setDeletedAt(Instant.now());
        membershipPlanRepository.save(plan);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "MembershipPlan", membershipPlanId));
    }

    MembershipPlan findOrThrow(UUID organizationId, UUID membershipPlanId) {
        return membershipPlanRepository.findActiveByIdAndOrganizationId(membershipPlanId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("MembershipPlan", membershipPlanId));
    }

    private void applyFields(
            MembershipPlan plan,
            String description,
            MembershipPlan.BillingCycle billingCycle,
            BigDecimal price,
            String currency,
            Integer sessionCredits) {
        plan.setDescription(description);
        plan.setBillingCycle(billingCycle);
        plan.setPrice(price);
        plan.setCurrency(currency);
        plan.setSessionCredits(sessionCredits);
    }
}
