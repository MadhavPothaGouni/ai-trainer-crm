package com.aitrainercrm.platform.calendar.service;

import com.aitrainercrm.platform.account.repository.AccountRepository;
import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.calendar.dto.AddAttendeeRequest;
import com.aitrainercrm.platform.calendar.dto.CreateCalendarEventRequest;
import com.aitrainercrm.platform.calendar.dto.UpdateCalendarEventRequest;
import com.aitrainercrm.platform.calendar.entity.CalendarEvent;
import com.aitrainercrm.platform.calendar.entity.CalendarEventAttendee;
import com.aitrainercrm.platform.calendar.repository.CalendarEventAttendeeRepository;
import com.aitrainercrm.platform.calendar.repository.CalendarEventRepository;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.DuplicateResourceException;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.common.util.CsvWriter;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.lead.repository.LeadRepository;
import com.aitrainercrm.platform.opportunity.repository.OpportunityRepository;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.ticket.repository.TicketRepository;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
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
 * Calendar events and their attendees. The event itself follows the same
 * owner-scoped shape as {@code TicketService}/{@code EmailMessageService};
 * attendee mutations follow {@code CampaignService}'s member-mutation
 * pattern - gated on the parent event's own UPDATE permission, no separate
 * attendee-level permission in the catalog (there isn't one, same reasoning
 * Quote/Order line items and Campaign members already established).
 */
@Service
@RequiredArgsConstructor
public class CalendarEventService {

    private static final Permission.Resource RESOURCE = Permission.Resource.CALENDAR_EVENT;

    private final CalendarEventRepository calendarEventRepository;
    private final CalendarEventAttendeeRepository attendeeRepository;
    private final AccountRepository accountRepository;
    private final ContactRepository contactRepository;
    private final OpportunityRepository opportunityRepository;
    private final LeadRepository leadRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<CalendarEvent> list(
            UserPrincipal principal, CalendarEvent.RelatedToType relatedToType, UUID relatedToId, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        UUID organizationId = principal.getOrganizationId();

        if (relatedToType != null && relatedToId != null) {
            return visibleOwnerIds
                    .map(ownerIds -> calendarEventRepository.findByOrganizationIdAndOwnerIdInAndRelatedToTypeAndRelatedToIdAndDeletedAtIsNullOrderByStartAtAsc(
                            organizationId, ownerIds, relatedToType, relatedToId, pageable))
                    .orElseGet(() -> calendarEventRepository.findByOrganizationIdAndRelatedToTypeAndRelatedToIdAndDeletedAtIsNullOrderByStartAtAsc(
                            organizationId, relatedToType, relatedToId, pageable));
        }

        return visibleOwnerIds
                .map(ownerIds -> calendarEventRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNullOrderByStartAtAsc(organizationId, ownerIds, pageable))
                .orElseGet(() -> calendarEventRepository.findByOrganizationIdAndDeletedAtIsNullOrderByStartAtAsc(organizationId, pageable));
    }

    @Transactional(readOnly = true)
    public CalendarEvent get(UserPrincipal principal, UUID eventId) {
        CalendarEvent event = findOrThrow(principal.getOrganizationId(), eventId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, event.getOwnerId());
        return event;
    }

    /** Backs GET /calendar-events/export (CALENDAR_EVENT:EXPORT) - same shape as EmailMessageService/CampaignService's export. */
    @Transactional(readOnly = true)
    public byte[] exportCsv(UserPrincipal principal) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.EXPORT);
        UUID organizationId = principal.getOrganizationId();
        List<CalendarEvent> calendarEvents = visibleOwnerIds
                .map(ownerIds -> calendarEventRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNullOrderByStartAtAsc(organizationId, ownerIds))
                .orElseGet(() -> calendarEventRepository.findByOrganizationIdAndDeletedAtIsNullOrderByStartAtAsc(organizationId));

        CsvWriter csv = new CsvWriter().row(
                "Title", "Location", "Start At", "End At", "All Day", "Related To Type", "Related To Id", "Created At");
        for (CalendarEvent event : calendarEvents) {
            csv.row(
                    event.getTitle(), event.getLocation(), event.getStartAt(), event.getEndAt(), event.isAllDay(),
                    event.getRelatedToType(), event.getRelatedToId(), event.getCreatedAt());
        }
        return csv.toBytes();
    }

    @Transactional
    public CalendarEvent create(UserPrincipal principal, CreateCalendarEventRequest request) {
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, request.ownerId());
        validateTimes(request.startAt(), request.endAt());
        validateRelatedTo(principal.getOrganizationId(), request.relatedToType(), request.relatedToId());

        CalendarEvent event = new CalendarEvent(principal.getOrganizationId(), request.title(), request.startAt(), request.endAt(), ownerId);
        event.setDescription(request.description());
        event.setLocation(request.location());
        event.setAllDay(request.allDay());
        event.setRelatedToType(request.relatedToType());
        event.setRelatedToId(request.relatedToId());
        calendarEventRepository.save(event);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "CalendarEvent", event.getId()));
        return event;
    }

    @Transactional
    public CalendarEvent update(UserPrincipal principal, UUID eventId, UpdateCalendarEventRequest request) {
        CalendarEvent event = findOrThrow(principal.getOrganizationId(), eventId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, event.getOwnerId());
        validateTimes(request.startAt(), request.endAt());
        validateRelatedTo(principal.getOrganizationId(), request.relatedToType(), request.relatedToId());

        event.setTitle(request.title());
        event.setDescription(request.description());
        event.setLocation(request.location());
        event.setStartAt(request.startAt());
        event.setEndAt(request.endAt());
        event.setAllDay(request.allDay());
        event.setRelatedToType(request.relatedToType());
        event.setRelatedToId(request.relatedToId());
        calendarEventRepository.save(event);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "CalendarEvent", event.getId()));
        return event;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID eventId) {
        CalendarEvent event = findOrThrow(principal.getOrganizationId(), eventId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, event.getOwnerId());

        event.setDeletedAt(Instant.now());
        calendarEventRepository.save(event);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "CalendarEvent", eventId));
    }

    @Transactional
    public CalendarEvent assignOwner(UserPrincipal principal, UUID eventId, UUID newOwnerId) {
        CalendarEvent event = findOrThrow(principal.getOrganizationId(), eventId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.ASSIGN, event.getOwnerId());
        assertUserInOrganization(principal.getOrganizationId(), newOwnerId);

        event.setOwnerId(newOwnerId);
        calendarEventRepository.save(event);

        events.publishEvent(new CrmAuditEvents.RecordAssigned(principal.getId(), principal.getOrganizationId(), "CalendarEvent", event.getId(), newOwnerId));
        return event;
    }

    @Transactional(readOnly = true)
    public List<CalendarEventAttendee> getAttendees(UserPrincipal principal, UUID eventId) {
        CalendarEvent event = findOrThrow(principal.getOrganizationId(), eventId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, event.getOwnerId());
        return attendeeRepository.findByCalendarEventIdOrderByCreatedAtAsc(eventId);
    }

    @Transactional
    public CalendarEventAttendee addAttendee(UserPrincipal principal, UUID eventId, AddAttendeeRequest request) {
        CalendarEvent event = findOrThrow(principal.getOrganizationId(), eventId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, event.getOwnerId());

        boolean hasUser = request.userId() != null;
        boolean hasEmail = request.externalEmail() != null && !request.externalEmail().isBlank();
        if (hasUser == hasEmail) {
            throw new BusinessException(
                    "CALENDAR_ATTENDEE_INVALID_TARGET", "An attendee must reference exactly one of a user or an external email", HttpStatus.BAD_REQUEST);
        }

        if (hasUser) {
            assertUserInOrganization(principal.getOrganizationId(), request.userId());
            if (attendeeRepository.existsByCalendarEventIdAndUserId(eventId, request.userId())) {
                throw new DuplicateResourceException("This user is already an attendee of this event");
            }
        } else if (attendeeRepository.existsByCalendarEventIdAndExternalEmail(eventId, request.externalEmail())) {
            throw new DuplicateResourceException("This email is already an attendee of this event");
        }

        CalendarEventAttendee attendee = new CalendarEventAttendee(eventId, request.userId(), hasUser ? null : request.externalEmail());
        attendeeRepository.save(attendee);
        return attendee;
    }

    @Transactional
    public CalendarEventAttendee updateAttendeeResponse(
            UserPrincipal principal, UUID eventId, UUID attendeeId, CalendarEventAttendee.ResponseStatus responseStatus) {
        CalendarEvent event = findOrThrow(principal.getOrganizationId(), eventId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, event.getOwnerId());
        CalendarEventAttendee attendee = attendeeRepository.findByIdAndCalendarEventId(attendeeId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("CalendarEventAttendee", attendeeId));

        attendee.setResponseStatus(responseStatus);
        attendeeRepository.save(attendee);
        return attendee;
    }

    @Transactional
    public void removeAttendee(UserPrincipal principal, UUID eventId, UUID attendeeId) {
        CalendarEvent event = findOrThrow(principal.getOrganizationId(), eventId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, event.getOwnerId());
        CalendarEventAttendee attendee = attendeeRepository.findByIdAndCalendarEventId(attendeeId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("CalendarEventAttendee", attendeeId));
        attendeeRepository.delete(attendee);
    }

    private CalendarEvent findOrThrow(UUID organizationId, UUID eventId) {
        return calendarEventRepository.findActiveByIdAndOrganizationId(eventId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("CalendarEvent", eventId));
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

    private void validateTimes(Instant startAt, Instant endAt) {
        if (endAt.isBefore(startAt)) {
            throw new BusinessException("CALENDAR_EVENT_INVALID_TIMES", "End time cannot be before start time", HttpStatus.BAD_REQUEST);
        }
    }

    /** relatedToType and relatedToId must be both-null or both-set - unlike EmailMessageService's version of this check, neither field is @NotNull on the request DTO since an event doesn't have to be about a CRM record at all. */
    private void validateRelatedTo(UUID organizationId, CalendarEvent.RelatedToType relatedToType, UUID relatedToId) {
        boolean hasType = relatedToType != null;
        boolean hasId = relatedToId != null;
        if (hasType != hasId) {
            throw new BusinessException(
                    "CALENDAR_EVENT_INVALID_RELATED_TO", "relatedToType and relatedToId must both be set or both be omitted", HttpStatus.BAD_REQUEST);
        }
        if (!hasType) {
            return;
        }
        boolean exists = switch (relatedToType) {
            case ACCOUNT -> accountRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(relatedToId, organizationId);
            case CONTACT -> contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(relatedToId, organizationId);
            case OPPORTUNITY -> opportunityRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(relatedToId, organizationId);
            case LEAD -> leadRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(relatedToId, organizationId);
            case TICKET -> ticketRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(relatedToId, organizationId);
        };
        if (!exists) {
            throw new ResourceNotFoundException(relatedToType.name(), relatedToId);
        }
    }
}
