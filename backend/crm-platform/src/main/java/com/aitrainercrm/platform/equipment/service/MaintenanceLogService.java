package com.aitrainercrm.platform.equipment.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.equipment.dto.CreateMaintenanceLogRequest;
import com.aitrainercrm.platform.equipment.dto.UpdateMaintenanceLogRequest;
import com.aitrainercrm.platform.equipment.entity.MaintenanceLog;
import com.aitrainercrm.platform.equipment.repository.MaintenanceLogRepository;
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
 * Service history for {@link com.aitrainercrm.platform.equipment.entity.Equipment} - see
 * {@link MaintenanceLog}'s javadoc for why this has no status field. Follows the same shape as
 * {@code MembershipService}/{@code ClassSessionService}: {@code resolveOwner} defaulting a null
 * {@code ownerId} to the caller (whoever performed the work), no invalid-transition machinery
 * since there's no status to transition.
 */
@Service
@RequiredArgsConstructor
public class MaintenanceLogService {

    private static final Permission.Resource RESOURCE = Permission.Resource.MAINTENANCE_LOG;

    private final MaintenanceLogRepository maintenanceLogRepository;
    private final EquipmentService equipmentService;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<MaintenanceLog> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleOwnerIds
                .map(ownerIds -> maintenanceLogRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(principal.getOrganizationId(), ownerIds, pageable))
                .orElseGet(() -> maintenanceLogRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable));
    }

    @Transactional(readOnly = true)
    public MaintenanceLog get(UserPrincipal principal, UUID maintenanceLogId) {
        MaintenanceLog log = findOrThrow(principal.getOrganizationId(), maintenanceLogId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, log.getOwnerId());
        return log;
    }

    @Transactional
    public MaintenanceLog create(UserPrincipal principal, CreateMaintenanceLogRequest request) {
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, request.ownerId());
        equipmentService.findOrThrow(principal.getOrganizationId(), request.equipmentId());

        MaintenanceLog log = new MaintenanceLog(principal.getOrganizationId(), request.equipmentId(), ownerId, request.performedAt());
        log.setType(request.type());
        log.setCost(request.cost());
        log.setNotes(request.notes());
        log.setNextDueDate(request.nextDueDate());
        maintenanceLogRepository.save(log);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "MaintenanceLog", log.getId()));
        return log;
    }

    @Transactional
    public MaintenanceLog update(UserPrincipal principal, UUID maintenanceLogId, UpdateMaintenanceLogRequest request) {
        MaintenanceLog log = findOrThrow(principal.getOrganizationId(), maintenanceLogId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, log.getOwnerId());

        log.setPerformedAt(request.performedAt());
        log.setType(request.type());
        log.setCost(request.cost());
        log.setNotes(request.notes());
        log.setNextDueDate(request.nextDueDate());
        maintenanceLogRepository.save(log);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "MaintenanceLog", log.getId()));
        return log;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID maintenanceLogId) {
        MaintenanceLog log = findOrThrow(principal.getOrganizationId(), maintenanceLogId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, log.getOwnerId());

        log.setDeletedAt(Instant.now());
        maintenanceLogRepository.save(log);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "MaintenanceLog", maintenanceLogId));
    }

    private MaintenanceLog findOrThrow(UUID organizationId, UUID maintenanceLogId) {
        return maintenanceLogRepository.findActiveByIdAndOrganizationId(maintenanceLogId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("MaintenanceLog", maintenanceLogId));
    }

    private UUID resolveOwner(UserPrincipal principal, Permission.Action action, UUID requestedOwnerId) {
        if (requestedOwnerId == null || requestedOwnerId.equals(principal.getId())) {
            return principal.getId();
        }
        if (scopeAuthorizationService.highestGranted(principal, RESOURCE, action) != ScopeAuthorizationService.Access.ORGANIZATION) {
            throw new ForbiddenException("You can only " + action.name().toLowerCase(Locale.ROOT) + " logs assigned to yourself");
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
}
