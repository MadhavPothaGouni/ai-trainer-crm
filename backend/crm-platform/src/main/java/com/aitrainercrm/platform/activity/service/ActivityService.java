package com.aitrainercrm.platform.activity.service;

import com.aitrainercrm.platform.account.repository.AccountRepository;
import com.aitrainercrm.platform.activity.dto.CreateActivityRequest;
import com.aitrainercrm.platform.activity.dto.UpdateActivityRequest;
import com.aitrainercrm.platform.activity.entity.Activity;
import com.aitrainercrm.platform.activity.repository.ActivityRepository;
import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.lead.repository.LeadRepository;
import com.aitrainercrm.platform.opportunity.repository.OpportunityRepository;
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
 * Calls/emails/meetings/tasks/notes logged against an Account/Contact/
 * Opportunity/Lead. See ScopeAuthorizationService for how OWN/TEAM/
 * DEPARTMENT/ORGANIZATION-scoped ACTIVITY permissions get enforced - same
 * pattern as AccountService, just with one extra step (validateRelatedTo)
 * since an Activity's "what this is about" reference isn't a single fixed
 * entity type the way Contact.accountId is.
 */
@Service
@RequiredArgsConstructor
public class ActivityService {

    private static final Permission.Resource RESOURCE = Permission.Resource.ACTIVITY;

    private final ActivityRepository activityRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final ContactRepository contactRepository;
    private final OpportunityRepository opportunityRepository;
    private final LeadRepository leadRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    /** relatedToType/relatedToId are optional - when both are given, the list is additionally filtered to that one record's timeline. */
    @Transactional(readOnly = true)
    public Page<Activity> list(
            UserPrincipal principal, Activity.RelatedToType relatedToType, UUID relatedToId, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        UUID organizationId = principal.getOrganizationId();

        if (relatedToType != null && relatedToId != null) {
            return visibleOwnerIds
                    .map(ownerIds -> activityRepository.findByOrganizationIdAndOwnerIdInAndRelatedToTypeAndRelatedToId(
                            organizationId, ownerIds, relatedToType, relatedToId, pageable))
                    .orElseGet(() -> activityRepository.findByOrganizationIdAndRelatedToTypeAndRelatedToId(
                            organizationId, relatedToType, relatedToId, pageable));
        }

        return visibleOwnerIds
                .map(ownerIds -> activityRepository.findByOrganizationIdAndOwnerIdIn(organizationId, ownerIds, pageable))
                .orElseGet(() -> activityRepository.findByOrganizationId(organizationId, pageable));
    }

    @Transactional(readOnly = true)
    public Activity get(UserPrincipal principal, UUID activityId) {
        Activity activity = findOrThrow(principal.getOrganizationId(), activityId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, activity.getOwnerId());
        return activity;
    }

    @Transactional
    public Activity create(UserPrincipal principal, CreateActivityRequest request) {
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, request.ownerId());
        validateRelatedTo(principal.getOrganizationId(), request.relatedToType(), request.relatedToId());

        Activity activity = new Activity(
                principal.getOrganizationId(), request.type(), request.subject(), request.relatedToType(), request.relatedToId(), ownerId);
        activity.setDescription(request.description());
        activity.setPriority(request.priority());
        activity.setDueAt(request.dueAt());
        activityRepository.save(activity);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "Activity", activity.getId()));
        return activity;
    }

    @Transactional
    public Activity update(UserPrincipal principal, UUID activityId, UpdateActivityRequest request) {
        Activity activity = findOrThrow(principal.getOrganizationId(), activityId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, activity.getOwnerId());
        validateRelatedTo(principal.getOrganizationId(), request.relatedToType(), request.relatedToId());

        activity.setType(request.type());
        activity.setSubject(request.subject());
        activity.setDescription(request.description());
        activity.setPriority(request.priority());
        activity.setDueAt(request.dueAt());
        activity.setRelatedToType(request.relatedToType());
        activity.setRelatedToId(request.relatedToId());
        activityRepository.save(activity);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Activity", activity.getId()));
        return activity;
    }

    @Transactional
    public Activity updateStatus(UserPrincipal principal, UUID activityId, Activity.Status status) {
        Activity activity = findOrThrow(principal.getOrganizationId(), activityId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, activity.getOwnerId());

        activity.setStatus(status);
        activity.setCompletedAt(status == Activity.Status.COMPLETED ? Instant.now() : null);
        activityRepository.save(activity);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Activity", activity.getId()));
        return activity;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID activityId) {
        Activity activity = findOrThrow(principal.getOrganizationId(), activityId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, activity.getOwnerId());

        activityRepository.delete(activity);
        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "Activity", activityId));
    }

    @Transactional
    public Activity assignOwner(UserPrincipal principal, UUID activityId, UUID newOwnerId) {
        Activity activity = findOrThrow(principal.getOrganizationId(), activityId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.ASSIGN, activity.getOwnerId());
        assertUserInOrganization(principal.getOrganizationId(), newOwnerId);

        activity.setOwnerId(newOwnerId);
        activityRepository.save(activity);

        events.publishEvent(new CrmAuditEvents.RecordAssigned(principal.getId(), principal.getOrganizationId(), "Activity", activity.getId(), newOwnerId));
        return activity;
    }

    private Activity findOrThrow(UUID organizationId, UUID activityId) {
        return activityRepository.findByIdAndOrganizationId(activityId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Activity", activityId));
    }

    /** Same "null/self is free, anyone else needs ORGANIZATION scope" rule as AccountService#resolveOwner. */
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
        boolean exists = userRepository.findActiveById(userId)
                .map(u -> organizationId.equals(u.getOrganizationId()))
                .orElse(false);
        if (!exists) {
            throw new ResourceNotFoundException("User", userId);
        }
    }

    /** No FK to lean on (see V4's migration comment) - checks the reference exists, in this tenant, against whichever of the four repositories relatedToType names. */
    private void validateRelatedTo(UUID organizationId, Activity.RelatedToType relatedToType, UUID relatedToId) {
        boolean exists = switch (relatedToType) {
            case ACCOUNT -> accountRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(relatedToId, organizationId);
            case CONTACT -> contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(relatedToId, organizationId);
            case OPPORTUNITY -> opportunityRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(relatedToId, organizationId);
            case LEAD -> leadRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(relatedToId, organizationId);
        };
        if (!exists) {
            throw new ResourceNotFoundException(relatedToType.name(), relatedToId);
        }
    }
}
