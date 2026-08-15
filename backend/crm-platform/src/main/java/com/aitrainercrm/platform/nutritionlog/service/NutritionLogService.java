package com.aitrainercrm.platform.nutritionlog.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.nutritionlog.dto.CreateNutritionLogRequest;
import com.aitrainercrm.platform.nutritionlog.dto.UpdateNutritionLogRequest;
import com.aitrainercrm.platform.nutritionlog.entity.NutritionLog;
import com.aitrainercrm.platform.nutritionlog.repository.NutritionLogRepository;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One meal a client logged - see {@link NutritionLog}'s javadoc and V63's migration comment for
 * the backstory. Follows the same OWN/TEAM/DEPARTMENT/ORGANIZATION record-level authorization
 * shape as {@code ProgressPhotoService}, with {@code resolveOwner} defaulting a null
 * {@code ownerId} to the caller. No business-rule validation - unlike
 * {@code LoyaltyTransactionService}, there's nothing to check here beyond the usual existence
 * checks, since a logged meal's numbers are self-reported and not cross-validated against
 * anything else in the system.
 */
@Service
@RequiredArgsConstructor
public class NutritionLogService {

    private static final Permission.Resource RESOURCE = Permission.Resource.NUTRITION_LOG;

    private final NutritionLogRepository nutritionLogRepository;
    private final ContactRepository contactRepository;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<NutritionLog> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleOwnerIds
                .map(ownerIds -> nutritionLogRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(principal.getOrganizationId(), ownerIds, pageable))
                .orElseGet(() -> nutritionLogRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable));
    }

    @Transactional(readOnly = true)
    public NutritionLog get(UserPrincipal principal, UUID nutritionLogId) {
        NutritionLog log = findOrThrow(principal.getOrganizationId(), nutritionLogId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, log.getOwnerId());
        return log;
    }

    @Transactional
    public NutritionLog create(UserPrincipal principal, CreateNutritionLogRequest request) {
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, request.ownerId());
        assertContactInOrganization(principal.getOrganizationId(), request.contactId());

        NutritionLog log = new NutritionLog(principal.getOrganizationId(), request.contactId(), ownerId, request.loggedAt(), request.mealType());
        log.setCalories(request.calories());
        log.setProteinGrams(request.proteinGrams());
        log.setCarbGrams(request.carbGrams());
        log.setFatGrams(request.fatGrams());
        log.setNotes(request.notes());
        nutritionLogRepository.save(log);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "NutritionLog", log.getId()));
        return log;
    }

    @Transactional
    public NutritionLog update(UserPrincipal principal, UUID nutritionLogId, UpdateNutritionLogRequest request) {
        NutritionLog log = findOrThrow(principal.getOrganizationId(), nutritionLogId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, log.getOwnerId());

        log.setLoggedAt(request.loggedAt());
        log.setMealType(request.mealType());
        log.setCalories(request.calories());
        log.setProteinGrams(request.proteinGrams());
        log.setCarbGrams(request.carbGrams());
        log.setFatGrams(request.fatGrams());
        log.setNotes(request.notes());
        nutritionLogRepository.save(log);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "NutritionLog", log.getId()));
        return log;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID nutritionLogId) {
        NutritionLog log = findOrThrow(principal.getOrganizationId(), nutritionLogId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, log.getOwnerId());

        log.setDeletedAt(Instant.now());
        nutritionLogRepository.save(log);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "NutritionLog", nutritionLogId));
    }

    private NutritionLog findOrThrow(UUID organizationId, UUID nutritionLogId) {
        return nutritionLogRepository.findActiveByIdAndOrganizationId(nutritionLogId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("NutritionLog", nutritionLogId));
    }

    private UUID resolveOwner(UserPrincipal principal, Permission.Action action, UUID requestedOwnerId) {
        if (requestedOwnerId == null || requestedOwnerId.equals(principal.getId())) {
            return principal.getId();
        }
        if (scopeAuthorizationService.highestGranted(principal, RESOURCE, action) != ScopeAuthorizationService.Access.ORGANIZATION) {
            throw new ForbiddenException("You can only " + action.name().toLowerCase(Locale.ROOT) + " nutrition logs you manage");
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
