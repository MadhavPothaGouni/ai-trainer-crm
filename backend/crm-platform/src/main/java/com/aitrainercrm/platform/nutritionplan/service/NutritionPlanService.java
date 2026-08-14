package com.aitrainercrm.platform.nutritionplan.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.nutritionplan.dto.CreateNutritionPlanRequest;
import com.aitrainercrm.platform.nutritionplan.dto.UpdateNutritionPlanRequest;
import com.aitrainercrm.platform.nutritionplan.entity.NutritionPlan;
import com.aitrainercrm.platform.nutritionplan.repository.NutritionPlanRepository;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nutrition plans - see {@link NutritionPlan}'s javadoc and V40's migration comment for the
 * backstory. Follows the exact same shape as {@code ClientGoalService}/{@code ContractService}:
 * OWN/TEAM/DEPARTMENT/ORGANIZATION record-level authorization via
 * {@link ScopeAuthorizationService}, {@code resolveOwner} defaulting a null {@code ownerId} to
 * the caller.
 */
@Service
@RequiredArgsConstructor
public class NutritionPlanService {

    private static final Permission.Resource RESOURCE = Permission.Resource.NUTRITION_PLAN;

    private final NutritionPlanRepository nutritionPlanRepository;
    private final ContactRepository contactRepository;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<NutritionPlan> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleOwnerIds
                .map(ownerIds -> nutritionPlanRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(principal.getOrganizationId(), ownerIds, pageable))
                .orElseGet(() -> nutritionPlanRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable));
    }

    @Transactional(readOnly = true)
    public NutritionPlan get(UserPrincipal principal, UUID nutritionPlanId) {
        NutritionPlan plan = findOrThrow(principal.getOrganizationId(), nutritionPlanId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, plan.getOwnerId());
        return plan;
    }

    @Transactional
    public NutritionPlan create(UserPrincipal principal, CreateNutritionPlanRequest request) {
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, request.ownerId());
        assertContactInOrganization(principal.getOrganizationId(), request.contactId());
        assertDatesValid(request.startDate(), request.endDate());

        NutritionPlan plan = new NutritionPlan(principal.getOrganizationId(), request.contactId(), ownerId, request.title());
        plan.setDailyCalorieTarget(request.dailyCalorieTarget());
        plan.setProteinTargetGrams(request.proteinTargetGrams());
        plan.setCarbTargetGrams(request.carbTargetGrams());
        plan.setFatTargetGrams(request.fatTargetGrams());
        plan.setStartDate(request.startDate());
        plan.setEndDate(request.endDate());
        plan.setNotes(request.notes());
        nutritionPlanRepository.save(plan);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "NutritionPlan", plan.getId()));
        return plan;
    }

    @Transactional
    public NutritionPlan update(UserPrincipal principal, UUID nutritionPlanId, UpdateNutritionPlanRequest request) {
        NutritionPlan plan = findOrThrow(principal.getOrganizationId(), nutritionPlanId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, plan.getOwnerId());
        assertDatesValid(request.startDate(), request.endDate());

        plan.setTitle(request.title());
        plan.setDailyCalorieTarget(request.dailyCalorieTarget());
        plan.setProteinTargetGrams(request.proteinTargetGrams());
        plan.setCarbTargetGrams(request.carbTargetGrams());
        plan.setFatTargetGrams(request.fatTargetGrams());
        plan.setStartDate(request.startDate());
        plan.setEndDate(request.endDate());
        plan.setNotes(request.notes());
        nutritionPlanRepository.save(plan);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "NutritionPlan", plan.getId()));
        return plan;
    }

    /**
     * No invalid-transition checks, same restraint {@code ContractService#updateStatus}'s javadoc
     * documents for contracts.status - pulling an ARCHIVED plan back to ACTIVE because a client
     * resumes it is a legitimate correction, not an invalid transition.
     */
    @Transactional
    public NutritionPlan updateStatus(UserPrincipal principal, UUID nutritionPlanId, NutritionPlan.Status newStatus) {
        NutritionPlan plan = findOrThrow(principal.getOrganizationId(), nutritionPlanId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, plan.getOwnerId());

        plan.setStatus(newStatus);
        nutritionPlanRepository.save(plan);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "NutritionPlan", plan.getId()));
        return plan;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID nutritionPlanId) {
        NutritionPlan plan = findOrThrow(principal.getOrganizationId(), nutritionPlanId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, plan.getOwnerId());

        plan.setDeletedAt(Instant.now());
        nutritionPlanRepository.save(plan);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "NutritionPlan", plan.getId()));
    }

    private NutritionPlan findOrThrow(UUID organizationId, UUID nutritionPlanId) {
        return nutritionPlanRepository.findActiveByIdAndOrganizationId(nutritionPlanId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("NutritionPlan", nutritionPlanId));
    }

    /** Both endpoints are optional (an ongoing plan may carry neither, or only a startDate) - only checked when both are present, unlike Contract's always-required pair. */
    private void assertDatesValid(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessException("NUTRITION_PLAN_INVALID_DATES", "End date cannot be before start date", HttpStatus.BAD_REQUEST);
        }
    }

    private UUID resolveOwner(UserPrincipal principal, Permission.Action action, UUID requestedOwnerId) {
        if (requestedOwnerId == null || requestedOwnerId.equals(principal.getId())) {
            return principal.getId();
        }
        if (scopeAuthorizationService.highestGranted(principal, RESOURCE, action) != ScopeAuthorizationService.Access.ORGANIZATION) {
            throw new ForbiddenException("You can only " + action.name().toLowerCase(Locale.ROOT) + " records assigned to yourself");
        }
        assertUserInOrganization(principal.getOrganizationId(), requestedOwnerId);
        return requestedOwnerId;
    }

    private void assertUserInOrganization(UUID organizationId, UUID userId) {
        boolean exists = userRepository.findActiveById(userId).map(u -> organizationId.equals(u.getOrganizationId())).orElse(false);
        if (!exists) {
            throw new ResourceNotFoundException("User", userId);
        }
    }

    private void assertContactInOrganization(UUID organizationId, UUID contactId) {
        if (!contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(contactId, organizationId)) {
            throw new ResourceNotFoundException("Contact", contactId);
        }
    }
}
