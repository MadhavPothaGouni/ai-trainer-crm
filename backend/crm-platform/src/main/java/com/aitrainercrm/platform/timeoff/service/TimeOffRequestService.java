package com.aitrainercrm.platform.timeoff.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.timeoff.dto.CreateTimeOffRequestRequest;
import com.aitrainercrm.platform.timeoff.dto.UpdateTimeOffRequestRequest;
import com.aitrainercrm.platform.timeoff.entity.TimeOffRequest;
import com.aitrainercrm.platform.timeoff.repository.TimeOffRequestRepository;
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
 * Time-off requests - see {@link TimeOffRequest}'s javadoc and V49's migration comment for the
 * backstory. Follows the exact same shape as {@code ClientGoalService}: OWN/TEAM/DEPARTMENT/
 * ORGANIZATION record-level authorization via {@link ScopeAuthorizationService},
 * {@code resolveOwner} defaulting a null {@code ownerId} to the caller.
 */
@Service
@RequiredArgsConstructor
public class TimeOffRequestService {

    private static final Permission.Resource RESOURCE = Permission.Resource.TIME_OFF_REQUEST;

    private final TimeOffRequestRepository timeOffRequestRepository;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<TimeOffRequest> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleOwnerIds
                .map(ownerIds -> timeOffRequestRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(principal.getOrganizationId(), ownerIds, pageable))
                .orElseGet(() -> timeOffRequestRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable));
    }

    @Transactional(readOnly = true)
    public TimeOffRequest get(UserPrincipal principal, UUID timeOffRequestId) {
        TimeOffRequest request = findOrThrow(principal.getOrganizationId(), timeOffRequestId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, request.getOwnerId());
        return request;
    }

    @Transactional
    public TimeOffRequest create(UserPrincipal principal, CreateTimeOffRequestRequest request) {
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, request.ownerId());

        TimeOffRequest timeOffRequest = new TimeOffRequest(principal.getOrganizationId(), ownerId, request.startDate(), request.endDate());
        timeOffRequest.setType(request.type());
        timeOffRequest.setReason(request.reason());
        timeOffRequest.setNotes(request.notes());
        timeOffRequestRepository.save(timeOffRequest);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "TimeOffRequest", timeOffRequest.getId()));
        return timeOffRequest;
    }

    @Transactional
    public TimeOffRequest update(UserPrincipal principal, UUID timeOffRequestId, UpdateTimeOffRequestRequest request) {
        TimeOffRequest timeOffRequest = findOrThrow(principal.getOrganizationId(), timeOffRequestId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, timeOffRequest.getOwnerId());

        timeOffRequest.setStartDate(request.startDate());
        timeOffRequest.setEndDate(request.endDate());
        timeOffRequest.setType(request.type());
        timeOffRequest.setReason(request.reason());
        timeOffRequest.setNotes(request.notes());
        timeOffRequestRepository.save(timeOffRequest);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "TimeOffRequest", timeOffRequest.getId()));
        return timeOffRequest;
    }

    /**
     * No invalid-transition checks, same restraint {@code ClientGoalService#updateStatus}'s
     * javadoc documents - approving a previously-denied request is a legitimate correction.
     * {@code approvedAt} is stamped the first time status moves to APPROVED and never overwritten
     * afterward.
     */
    @Transactional
    public TimeOffRequest updateStatus(UserPrincipal principal, UUID timeOffRequestId, TimeOffRequest.Status newStatus) {
        TimeOffRequest timeOffRequest = findOrThrow(principal.getOrganizationId(), timeOffRequestId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, timeOffRequest.getOwnerId());

        if (newStatus == TimeOffRequest.Status.APPROVED && timeOffRequest.getApprovedAt() == null) {
            timeOffRequest.setApprovedAt(Instant.now());
        }
        timeOffRequest.setStatus(newStatus);
        timeOffRequestRepository.save(timeOffRequest);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "TimeOffRequest", timeOffRequest.getId()));
        return timeOffRequest;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID timeOffRequestId) {
        TimeOffRequest timeOffRequest = findOrThrow(principal.getOrganizationId(), timeOffRequestId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, timeOffRequest.getOwnerId());

        timeOffRequest.setDeletedAt(Instant.now());
        timeOffRequestRepository.save(timeOffRequest);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "TimeOffRequest", timeOffRequestId));
    }

    private TimeOffRequest findOrThrow(UUID organizationId, UUID timeOffRequestId) {
        return timeOffRequestRepository.findActiveByIdAndOrganizationId(timeOffRequestId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("TimeOffRequest", timeOffRequestId));
    }

    private UUID resolveOwner(UserPrincipal principal, Permission.Action action, UUID requestedOwnerId) {
        if (requestedOwnerId == null || requestedOwnerId.equals(principal.getId())) {
            return principal.getId();
        }
        if (scopeAuthorizationService.highestGranted(principal, RESOURCE, action) != ScopeAuthorizationService.Access.ORGANIZATION) {
            throw new ForbiddenException("You can only " + action.name().toLowerCase(Locale.ROOT) + " requests assigned to yourself");
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
