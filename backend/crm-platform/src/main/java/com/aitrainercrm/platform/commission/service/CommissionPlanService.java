package com.aitrainercrm.platform.commission.service;

import com.aitrainercrm.platform.commission.dto.CreateCommissionPlanRequest;
import com.aitrainercrm.platform.commission.dto.UpdateCommissionPlanRequest;
import com.aitrainercrm.platform.commission.entity.CommissionPlan;
import com.aitrainercrm.platform.commission.repository.CommissionPlanRepository;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.organization.repository.TeamRepository;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CommissionPlan CRUD - admin config, the same third-kind shape SLA_POLICY/TERRITORY_RULE/
 * LEAD_SCORING_RULE/SALES_GOAL/REGION already use (COMMISSION_PLAN:*:ORGANIZATION only, no {@link
 * com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService} call anywhere).
 * {@link #assertExactlyOneTarget} re-validates the "exactly one of owner/team" rule at the
 * application level even though {@code chk_commission_plans_exactly_one_target} (V29) already
 * enforces it in the database, the same defense-in-depth {@code SalesGoalService} documents for
 * its identical constraint - a clean 400 with a real message beats a raw
 * DataIntegrityViolationException.
 */
@Service
@RequiredArgsConstructor
public class CommissionPlanService {

    private final CommissionPlanRepository commissionPlanRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;

    @Transactional(readOnly = true)
    public Page<CommissionPlan> list(UUID organizationId, Pageable pageable) {
        return commissionPlanRepository.findByOrganizationIdOrderByNameAsc(organizationId, pageable);
    }

    @Transactional(readOnly = true)
    public CommissionPlan get(UUID organizationId, UUID planId) {
        return findOrThrow(organizationId, planId);
    }

    @Transactional
    public CommissionPlan create(UUID organizationId, CreateCommissionPlanRequest request) {
        assertExactlyOneTarget(request.ownerUserId(), request.teamId());
        assertOwnerInOrganization(organizationId, request.ownerUserId());
        assertTeamInOrganization(organizationId, request.teamId());

        CommissionPlan plan = new CommissionPlan(
                organizationId, request.name(), request.ownerUserId(), request.teamId(), request.rateType(), request.rate());
        commissionPlanRepository.save(plan);
        return plan;
    }

    @Transactional
    public CommissionPlan update(UUID organizationId, UUID planId, UpdateCommissionPlanRequest request) {
        CommissionPlan plan = findOrThrow(organizationId, planId);
        assertExactlyOneTarget(request.ownerUserId(), request.teamId());
        assertOwnerInOrganization(organizationId, request.ownerUserId());
        assertTeamInOrganization(organizationId, request.teamId());

        plan.setName(request.name());
        plan.setOwnerUserId(request.ownerUserId());
        plan.setTeamId(request.teamId());
        plan.setRateType(request.rateType());
        plan.setRate(request.rate());
        plan.setActive(request.active());
        commissionPlanRepository.save(plan);
        return plan;
    }

    @Transactional
    public void delete(UUID organizationId, UUID planId) {
        CommissionPlan plan = findOrThrow(organizationId, planId);
        commissionPlanRepository.delete(plan);
    }

    private void assertExactlyOneTarget(UUID ownerUserId, UUID teamId) {
        boolean hasOwner = ownerUserId != null;
        boolean hasTeam = teamId != null;
        if (hasOwner == hasTeam) {
            throw new BusinessException(
                    "COMMISSION_PLAN_TARGET", "Exactly one of ownerUserId or teamId must be set.", HttpStatus.BAD_REQUEST);
        }
    }

    private void assertOwnerInOrganization(UUID organizationId, UUID ownerUserId) {
        if (ownerUserId == null) return;
        boolean exists = userRepository.findActiveById(ownerUserId).map(u -> organizationId.equals(u.getOrganizationId())).orElse(false);
        if (!exists) throw new ResourceNotFoundException("User", ownerUserId);
    }

    private void assertTeamInOrganization(UUID organizationId, UUID teamId) {
        if (teamId == null) return;
        if (teamRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(teamId, organizationId).isEmpty()) {
            throw new ResourceNotFoundException("Team", teamId);
        }
    }

    private CommissionPlan findOrThrow(UUID organizationId, UUID planId) {
        return commissionPlanRepository.findByIdAndOrganizationId(planId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("CommissionPlan", planId));
    }
}
