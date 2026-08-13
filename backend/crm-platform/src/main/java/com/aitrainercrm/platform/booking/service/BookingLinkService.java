package com.aitrainercrm.platform.booking.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.booking.dto.BookSlotRequest;
import com.aitrainercrm.platform.booking.dto.CreateBookingLinkRequest;
import com.aitrainercrm.platform.booking.dto.CreateBookingSlotRequest;
import com.aitrainercrm.platform.booking.dto.UpdateBookingLinkRequest;
import com.aitrainercrm.platform.booking.entity.BookingLink;
import com.aitrainercrm.platform.booking.entity.BookingSlot;
import com.aitrainercrm.platform.booking.repository.BookingLinkRepository;
import com.aitrainercrm.platform.booking.repository.BookingSlotRepository;
import com.aitrainercrm.platform.calendar.dto.AddAttendeeRequest;
import com.aitrainercrm.platform.calendar.dto.CreateCalendarEventRequest;
import com.aitrainercrm.platform.calendar.entity.CalendarEvent;
import com.aitrainercrm.platform.calendar.service.CalendarEventService;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.DuplicateResourceException;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.contact.entity.Contact;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.lead.entity.Lead;
import com.aitrainercrm.platform.lead.repository.LeadRepository;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.time.Duration;
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
 * Booking links and their slots - see V33's migration comment for the module overview.
 * {@link BookingLink} follows {@code TicketService}'s exact owner-scoped shape: OWN/TEAM/
 * DEPARTMENT/ORGANIZATION authorization via {@link ScopeAuthorizationService}, {@link
 * #resolveOwner} defaulting a null {@code ownerId} to the caller. {@link BookingSlot} add/remove
 * follows {@code SequenceService}'s child-row shape - gated on the parent link's own UPDATE
 * permission, no permission of its own.
 *
 * <p>{@link #book} and {@link #cancel} are the part with no precedent elsewhere in this codebase:
 * they don't just flip {@code BookingSlot.status}, they drive {@link CalendarEventService} to
 * create/soft-delete a real {@link CalendarEvent} - two modules, one action. That has a real
 * consequence worth documenting rather than working around: {@link CalendarEventService#create}
 * runs its <em>own</em> {@code resolveOwner} check against the {@code CALENDAR_EVENT} permission,
 * using the booking link's owner as the requested calendar event owner. A caller who can {@code
 * book()} a teammate's link (TEAM/DEPARTMENT scope on {@code BOOKING_LINK}) but doesn't separately
 * hold ORGANIZATION scope on {@code CALENDAR_EVENT} will have the booking rejected by {@code
 * CalendarEventService}, not by this service - a deliberate, honest cross-module permission
 * interaction rather than a bypass.
 */
@Service
@RequiredArgsConstructor
public class BookingLinkService {

    private static final Permission.Resource RESOURCE = Permission.Resource.BOOKING_LINK;

    private final BookingLinkRepository bookingLinkRepository;
    private final BookingSlotRepository bookingSlotRepository;
    private final CalendarEventService calendarEventService;
    private final LeadRepository leadRepository;
    private final ContactRepository contactRepository;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<BookingLink> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleOwnerIds
                .map(ownerIds -> bookingLinkRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNullOrderByTitleAsc(
                        principal.getOrganizationId(), ownerIds, pageable))
                .orElseGet(() -> bookingLinkRepository.findByOrganizationIdAndDeletedAtIsNullOrderByTitleAsc(principal.getOrganizationId(), pageable));
    }

    @Transactional(readOnly = true)
    public BookingLink get(UserPrincipal principal, UUID bookingLinkId) {
        BookingLink link = findOrThrow(principal.getOrganizationId(), bookingLinkId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, link.getOwnerId());
        return link;
    }

    @Transactional(readOnly = true)
    public List<BookingSlot> getSlots(UserPrincipal principal, UUID bookingLinkId) {
        get(principal, bookingLinkId); // re-validates existence + access
        return bookingSlotRepository.findByBookingLinkIdOrderByStartAtAsc(bookingLinkId);
    }

    @Transactional
    public BookingLink create(UserPrincipal principal, CreateBookingLinkRequest request) {
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, request.ownerId());
        assertSlugAvailable(principal.getOrganizationId(), request.slug(), null);

        BookingLink link = new BookingLink(principal.getOrganizationId(), ownerId, request.title(), request.durationMinutes(), request.slug());
        link.setDescription(request.description());
        bookingLinkRepository.save(link);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "BookingLink", link.getId()));
        return link;
    }

    @Transactional
    public BookingLink update(UserPrincipal principal, UUID bookingLinkId, UpdateBookingLinkRequest request) {
        BookingLink link = findOrThrow(principal.getOrganizationId(), bookingLinkId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, link.getOwnerId());
        assertSlugAvailable(principal.getOrganizationId(), request.slug(), bookingLinkId);

        link.setTitle(request.title());
        link.setDescription(request.description());
        link.setDurationMinutes(request.durationMinutes());
        link.setSlug(request.slug());
        link.setActive(request.active());
        bookingLinkRepository.save(link);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "BookingLink", link.getId()));
        return link;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID bookingLinkId) {
        BookingLink link = findOrThrow(principal.getOrganizationId(), bookingLinkId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, link.getOwnerId());

        link.setDeletedAt(Instant.now());
        bookingLinkRepository.save(link);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "BookingLink", bookingLinkId));
    }

    /** Appends a fresh OPEN slot; endAt is startAt + the link's *current* durationMinutes, snapshotted onto the slot - see BookingSlot's javadoc. */
    @Transactional
    public BookingSlot addSlot(UserPrincipal principal, UUID bookingLinkId, CreateBookingSlotRequest request) {
        BookingLink link = findOrThrow(principal.getOrganizationId(), bookingLinkId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, link.getOwnerId());

        if (bookingSlotRepository.existsByBookingLinkIdAndStartAtAndStatusNot(bookingLinkId, request.startAt(), BookingSlot.Status.CANCELLED)) {
            throw new DuplicateResourceException("This link already has an open or booked slot at that time");
        }

        Instant endAt = request.startAt().plus(Duration.ofMinutes(link.getDurationMinutes()));
        BookingSlot slot = new BookingSlot(bookingLinkId, request.startAt(), endAt);
        return bookingSlotRepository.save(slot);
    }

    /** Only an OPEN slot can be removed directly - a booked slot has to be cancelled first, so the CalendarEvent it created gets cleaned up rather than left orphaned. */
    @Transactional
    public void removeSlot(UserPrincipal principal, UUID bookingLinkId, UUID slotId) {
        BookingLink link = findOrThrow(principal.getOrganizationId(), bookingLinkId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, link.getOwnerId());
        BookingSlot slot = findSlotOrThrow(bookingLinkId, slotId);

        if (!slot.isOpen()) {
            throw new BusinessException("BOOKING_SLOT_NOT_OPEN", "Only an open slot can be removed - cancel a booked slot first", HttpStatus.CONFLICT);
        }
        bookingSlotRepository.delete(slot);
    }

    /**
     * Books an OPEN slot for a Lead or Contact and creates the real {@link CalendarEvent} behind it
     * - see this class's javadoc for the cross-module permission interaction this implies. The
     * event's title/description come from the {@link BookingLink}; an external attendee is added
     * from the target's email when one is on file (silently skipped otherwise - a missing email
     * isn't a reason to fail the booking itself).
     */
    @Transactional
    public BookingSlot book(UserPrincipal principal, UUID bookingLinkId, UUID slotId, BookSlotRequest request) {
        BookingLink link = findOrThrow(principal.getOrganizationId(), bookingLinkId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, link.getOwnerId());
        BookingSlot slot = findSlotOrThrow(bookingLinkId, slotId);

        if (!slot.isOpen()) {
            throw new BusinessException("BOOKING_SLOT_NOT_OPEN", "This slot is no longer open", HttpStatus.CONFLICT);
        }

        String targetEmail = resolveTargetEmail(principal.getOrganizationId(), request.targetType(), request.targetId());

        CalendarEvent event = calendarEventService.create(principal, new CreateCalendarEventRequest(
                link.getTitle(), link.getDescription(), null, slot.getStartAt(), slot.getEndAt(), false,
                CalendarEvent.RelatedToType.valueOf(request.targetType().name()), request.targetId(), link.getOwnerId()));
        if (targetEmail != null && !targetEmail.isBlank()) {
            calendarEventService.addAttendee(principal, event.getId(), new AddAttendeeRequest(null, targetEmail));
        }

        slot.setStatus(BookingSlot.Status.BOOKED);
        slot.setTargetType(request.targetType());
        slot.setTargetId(request.targetId());
        slot.setBookedAt(Instant.now());
        slot.setCalendarEventId(event.getId());
        bookingSlotRepository.save(slot);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "BookingSlot", slot.getId()));
        return slot;
    }

    /**
     * Moves a BOOKED slot to CANCELLED and soft-deletes the CalendarEvent it created - the slot
     * keeps its targetType/targetId/bookedAt as history rather than being reset, so "who this was
     * with" survives the cancellation even though the meeting itself no longer does.
     */
    @Transactional
    public BookingSlot cancel(UserPrincipal principal, UUID bookingLinkId, UUID slotId) {
        BookingLink link = findOrThrow(principal.getOrganizationId(), bookingLinkId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, link.getOwnerId());
        BookingSlot slot = findSlotOrThrow(bookingLinkId, slotId);

        if (slot.getStatus() != BookingSlot.Status.BOOKED) {
            throw new BusinessException("BOOKING_SLOT_NOT_BOOKED", "Only a booked slot can be cancelled", HttpStatus.CONFLICT);
        }

        if (slot.getCalendarEventId() != null) {
            calendarEventService.delete(principal, slot.getCalendarEventId());
        }
        slot.setStatus(BookingSlot.Status.CANCELLED);
        bookingSlotRepository.save(slot);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "BookingSlot", slot.getId()));
        return slot;
    }

    private String resolveTargetEmail(UUID organizationId, BookingSlot.TargetType targetType, UUID targetId) {
        return switch (targetType) {
            case LEAD -> {
                Lead lead = leadRepository.findActiveByIdAndOrganizationId(targetId, organizationId)
                        .orElseThrow(() -> new ResourceNotFoundException("Lead", targetId));
                yield lead.getEmail();
            }
            case CONTACT -> {
                Contact contact = contactRepository.findActiveByIdAndOrganizationId(targetId, organizationId)
                        .orElseThrow(() -> new ResourceNotFoundException("Contact", targetId));
                yield contact.getEmail();
            }
        };
    }

    private BookingLink findOrThrow(UUID organizationId, UUID bookingLinkId) {
        return bookingLinkRepository.findActiveByIdAndOrganizationId(bookingLinkId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("BookingLink", bookingLinkId));
    }

    private BookingSlot findSlotOrThrow(UUID bookingLinkId, UUID slotId) {
        return bookingSlotRepository.findByIdAndBookingLinkId(slotId, bookingLinkId)
                .orElseThrow(() -> new ResourceNotFoundException("BookingSlot", slotId));
    }

    private void assertSlugAvailable(UUID organizationId, String slug, UUID excludingLinkId) {
        boolean taken = excludingLinkId == null
                ? bookingLinkRepository.existsByOrganizationIdAndSlugAndDeletedAtIsNull(organizationId, slug)
                : bookingLinkRepository.existsByOrganizationIdAndSlugAndIdNotAndDeletedAtIsNull(organizationId, slug, excludingLinkId);
        if (taken) {
            throw new DuplicateResourceException("A booking link with this slug already exists");
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
}
