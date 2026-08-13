package com.aitrainercrm.platform.clientgoal.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.clientgoal.dto.CreateClientGoalRequest;
import com.aitrainercrm.platform.clientgoal.dto.UpdateClientGoalRequest;
import com.aitrainercrm.platform.clientgoal.entity.ClientGoal;
import com.aitrainercrm.platform.clientgoal.repository.ClientGoalRepository;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
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
 * Client goals - see {@link ClientGoal}'s javadoc and V36's migration comment for the backstory.
 * Follows the exact same shape as {@code ContractService}/{@code TicketService}: OWN/TEAM/
 * DEPARTMENT/ORGANIZATION record-level authorization via {@link ScopeAuthorizationService},
 * {@code resolveOwner} defaulting a null {@code ownerId} to the caller.
 */
@Service
@RequiredArgsConstructor
public class ClientGoalService {

    private static final Permission.Resource RESOURCE = Permission.Resource.CLIENT_GOAL;

    private final ClientGoalRepository clientGoalRepository;
    private final ContactRepository contactRepository;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<ClientGoal> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleOwnerIds
                .map(ownerIds -> clientGoalRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(principal.getOrganizationId(), ownerIds, pageable))
                .orElseGet(() -> clientGoalRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable));
    }

    @Transactional(readOnly = true)
    public ClientGoal get(UserPrincipal principal, UUID clientGoalId) {
        ClientGoal goal = findOrThrow(principal.getOrganizationId(), clientGoalId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, goal.getOwnerId());
        return goal;
    }

    @Transactional
    public ClientGoal create(UserPrincipal principal, CreateClientGoalRequest request) {
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, request.ownerId());
        assertContactInOrganization(principal.getOrganizationId(), request.contactId());

        ClientGoal goal = new ClientGoal(principal.getOrganizationId(), request.contactId(), ownerId, request.title());
        goal.setGoalType(request.goalType());
        goal.setMetricUnit(request.metricUnit());
        goal.setStartValue(request.startValue());
        goal.setTargetValue(request.targetValue());
        goal.setCurrentValue(request.currentValue());
        goal.setTargetDate(request.targetDate());
        goal.setNotes(request.notes());
        clientGoalRepository.save(goal);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "ClientGoal", goal.getId()));
        return goal;
    }

    @Transactional
    public ClientGoal update(UserPrincipal principal, UUID clientGoalId, UpdateClientGoalRequest request) {
        ClientGoal goal = findOrThrow(principal.getOrganizationId(), clientGoalId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, goal.getOwnerId());

        goal.setTitle(request.title());
        goal.setGoalType(request.goalType());
        goal.setMetricUnit(request.metricUnit());
        goal.setStartValue(request.startValue());
        goal.setTargetValue(request.targetValue());
        goal.setCurrentValue(request.currentValue());
        goal.setTargetDate(request.targetDate());
        goal.setNotes(request.notes());
        clientGoalRepository.save(goal);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "ClientGoal", goal.getId()));
        return goal;
    }

    /**
     * No invalid-transition checks, same restraint {@code ContractService#updateStatus}'s javadoc
     * documents for contracts.status - putting an abandoned goal back to ACTIVE is a legitimate
     * correction, not an invalid transition. {@code achievedAt} is stamped the first time status
     * moves to ACHIEVED and is never cleared or overwritten afterward.
     */
    @Transactional
    public ClientGoal updateStatus(UserPrincipal principal, UUID clientGoalId, ClientGoal.Status newStatus) {
        ClientGoal goal = findOrThrow(principal.getOrganizationId(), clientGoalId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, goal.getOwnerId());

        if (newStatus == ClientGoal.Status.ACHIEVED && goal.getAchievedAt() == null) {
            goal.setAchievedAt(Instant.now());
        }
        goal.setStatus(newStatus);
        clientGoalRepository.save(goal);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "ClientGoal", goal.getId()));
        return goal;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID clientGoalId) {
        ClientGoal goal = findOrThrow(principal.getOrganizationId(), clientGoalId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, goal.getOwnerId());

        goal.setDeletedAt(Instant.now());
        clientGoalRepository.save(goal);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "ClientGoal", goal.getId()));
    }

    private ClientGoal findOrThrow(UUID organizationId, UUID clientGoalId) {
        return clientGoalRepository.findActiveByIdAndOrganizationId(clientGoalId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("ClientGoal", clientGoalId));
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
