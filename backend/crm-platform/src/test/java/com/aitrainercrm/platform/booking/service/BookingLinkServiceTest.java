package com.aitrainercrm.platform.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.booking.dto.BookSlotRequest;
import com.aitrainercrm.platform.booking.dto.CreateBookingLinkRequest;
import com.aitrainercrm.platform.booking.dto.CreateBookingSlotRequest;
import com.aitrainercrm.platform.booking.entity.BookingLink;
import com.aitrainercrm.platform.booking.entity.BookingSlot;
import com.aitrainercrm.platform.booking.repository.BookingLinkRepository;
import com.aitrainercrm.platform.booking.repository.BookingSlotRepository;
import com.aitrainercrm.platform.calendar.dto.CreateCalendarEventRequest;
import com.aitrainercrm.platform.calendar.entity.CalendarEvent;
import com.aitrainercrm.platform.calendar.service.CalendarEventService;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.DuplicateResourceException;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.lead.entity.Lead;
import com.aitrainercrm.platform.lead.repository.LeadRepository;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * See {@link BookingLinkService}'s javadoc for the shape this mirrors ({@code TicketService}) and
 * the book/cancel cross-module notes. {@link CalendarEventService} is mocked wholesale - this test
 * only needs to prove BookingLinkService calls it with the right arguments at the right time, not
 * re-verify CalendarEventService's own internals (that's CalendarEventServiceTest's job, if one
 * exists, and SequenceEnrollmentServiceTest already established the "mock the collaborator service
 * wholesale" precedent for cross-service calls like this one).
 */
@ExtendWith(MockitoExtension.class)
class BookingLinkServiceTest {

    @Mock private BookingLinkRepository bookingLinkRepository;
    @Mock private BookingSlotRepository bookingSlotRepository;
    @Mock private CalendarEventService calendarEventService;
    @Mock private LeadRepository leadRepository;
    @Mock private ContactRepository contactRepository;
    @Mock private UserRepository userRepository;
    @Mock private ScopeAuthorizationService scopeAuthorizationService;
    @Mock private ApplicationEventPublisher events;

    private BookingLinkService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new BookingLinkService(
                bookingLinkRepository, bookingSlotRepository, calendarEventService, leadRepository, contactRepository, userRepository,
                scopeAuthorizationService, events);
    }

    private UserPrincipal principal(UUID userId) {
        return new UserPrincipal(userId, "rep@example.com", organizationId, List.of());
    }

    @Test
    void create_noOwnerIdRequested_selfAssignsTheCaller() {
        when(bookingLinkRepository.existsByOrganizationIdAndSlugAndDeletedAtIsNull(organizationId, "office-hours")).thenReturn(false);

        BookingLink result = service.create(principal(callerId), new CreateBookingLinkRequest("Office Hours", null, 30, "office-hours", null));

        assertThat(result.getOwnerId()).isEqualTo(callerId);
        assertThat(result.getSlug()).isEqualTo("office-hours");
        verify(bookingLinkRepository).save(result);
    }

    @Test
    void create_assigningSomeoneElseWithoutOrganizationScope_isForbidden() {
        UUID otherUserId = UUID.randomUUID();
        when(scopeAuthorizationService.highestGranted(any(), any(), any())).thenReturn(ScopeAuthorizationService.Access.TEAM);

        assertThatThrownBy(() -> service.create(principal(callerId), new CreateBookingLinkRequest("Office Hours", null, 30, "office-hours", otherUserId)))
                .isInstanceOf(ForbiddenException.class);
        verify(bookingLinkRepository, never()).save(any());
    }

    @Test
    void create_duplicateSlug_isRejected() {
        when(bookingLinkRepository.existsByOrganizationIdAndSlugAndDeletedAtIsNull(organizationId, "office-hours")).thenReturn(true);

        assertThatThrownBy(() -> service.create(principal(callerId), new CreateBookingLinkRequest("Office Hours", null, 30, "office-hours", null)))
                .isInstanceOf(DuplicateResourceException.class);
        verify(bookingLinkRepository, never()).save(any());
    }

    @Test
    void addSlot_computesEndAtFromLinkDuration() {
        UUID linkId = UUID.randomUUID();
        BookingLink link = link(linkId, callerId, 45);
        Instant startAt = Instant.parse("2026-09-01T15:00:00Z");
        when(bookingLinkRepository.findActiveByIdAndOrganizationId(linkId, organizationId)).thenReturn(Optional.of(link));
        when(bookingSlotRepository.existsByBookingLinkIdAndStartAtAndStatusNot(linkId, startAt, BookingSlot.Status.CANCELLED)).thenReturn(false);

        BookingSlot result = service.addSlot(principal(callerId), linkId, new CreateBookingSlotRequest(startAt));

        assertThat(result.getStartAt()).isEqualTo(startAt);
        assertThat(result.getEndAt()).isEqualTo(Instant.parse("2026-09-01T15:45:00Z"));
    }

    @Test
    void addSlot_conflictingTime_isRejected() {
        UUID linkId = UUID.randomUUID();
        BookingLink link = link(linkId, callerId, 30);
        Instant startAt = Instant.parse("2026-09-01T15:00:00Z");
        when(bookingLinkRepository.findActiveByIdAndOrganizationId(linkId, organizationId)).thenReturn(Optional.of(link));
        when(bookingSlotRepository.existsByBookingLinkIdAndStartAtAndStatusNot(linkId, startAt, BookingSlot.Status.CANCELLED)).thenReturn(true);

        assertThatThrownBy(() -> service.addSlot(principal(callerId), linkId, new CreateBookingSlotRequest(startAt)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void book_openSlot_createsCalendarEventAndTransitionsToBooked() {
        UUID linkId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        BookingLink link = link(linkId, callerId, 30);
        BookingSlot slot = slot(slotId, linkId);
        Lead lead = new Lead(organizationId, "Jordan", "Prospect", callerId);
        lead.setId(leadId);
        lead.setEmail("jordan@example.com");

        when(bookingLinkRepository.findActiveByIdAndOrganizationId(linkId, organizationId)).thenReturn(Optional.of(link));
        when(bookingSlotRepository.findByIdAndBookingLinkId(slotId, linkId)).thenReturn(Optional.of(slot));
        when(leadRepository.findActiveByIdAndOrganizationId(leadId, organizationId)).thenReturn(Optional.of(lead));
        CalendarEvent createdEvent = new CalendarEvent(organizationId, link.getTitle(), slot.getStartAt(), slot.getEndAt(), link.getOwnerId());
        UUID eventId = UUID.randomUUID();
        createdEvent.setId(eventId);
        when(calendarEventService.create(any(), any(CreateCalendarEventRequest.class))).thenReturn(createdEvent);

        BookingSlot result = service.book(principal(callerId), linkId, slotId, new BookSlotRequest(BookingSlot.TargetType.LEAD, leadId));

        assertThat(result.getStatus()).isEqualTo(BookingSlot.Status.BOOKED);
        assertThat(result.getTargetId()).isEqualTo(leadId);
        assertThat(result.getCalendarEventId()).isEqualTo(eventId);
        assertThat(result.getBookedAt()).isNotNull();
        verify(calendarEventService).addAttendee(any(), org.mockito.ArgumentMatchers.eq(eventId), any());
    }

    @Test
    void book_alreadyBookedSlot_isRejected() {
        UUID linkId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        BookingLink link = link(linkId, callerId, 30);
        BookingSlot slot = slot(slotId, linkId);
        slot.setStatus(BookingSlot.Status.BOOKED);
        when(bookingLinkRepository.findActiveByIdAndOrganizationId(linkId, organizationId)).thenReturn(Optional.of(link));
        when(bookingSlotRepository.findByIdAndBookingLinkId(slotId, linkId)).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> service.book(principal(callerId), linkId, slotId, new BookSlotRequest(BookingSlot.TargetType.LEAD, UUID.randomUUID())))
                .isInstanceOf(BusinessException.class);
        verify(calendarEventService, never()).create(any(), any());
    }

    @Test
    void cancel_bookedSlot_softDeletesTheCalendarEventAndKeepsHistory() {
        UUID linkId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        BookingLink link = link(linkId, callerId, 30);
        BookingSlot slot = slot(slotId, linkId);
        slot.setStatus(BookingSlot.Status.BOOKED);
        slot.setTargetType(BookingSlot.TargetType.LEAD);
        slot.setTargetId(leadId);
        slot.setCalendarEventId(eventId);
        when(bookingLinkRepository.findActiveByIdAndOrganizationId(linkId, organizationId)).thenReturn(Optional.of(link));
        when(bookingSlotRepository.findByIdAndBookingLinkId(slotId, linkId)).thenReturn(Optional.of(slot));

        BookingSlot result = service.cancel(principal(callerId), linkId, slotId);

        assertThat(result.getStatus()).isEqualTo(BookingSlot.Status.CANCELLED);
        assertThat(result.getTargetId()).isEqualTo(leadId);
        verify(calendarEventService).delete(any(), org.mockito.ArgumentMatchers.eq(eventId));
    }

    @Test
    void cancel_openSlot_isRejected() {
        UUID linkId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        BookingLink link = link(linkId, callerId, 30);
        BookingSlot slot = slot(slotId, linkId);
        when(bookingLinkRepository.findActiveByIdAndOrganizationId(linkId, organizationId)).thenReturn(Optional.of(link));
        when(bookingSlotRepository.findByIdAndBookingLinkId(slotId, linkId)).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> service.cancel(principal(callerId), linkId, slotId)).isInstanceOf(BusinessException.class);
        verify(calendarEventService, never()).delete(any(), any());
    }

    private BookingLink link(UUID id, UUID ownerId, int durationMinutes) {
        BookingLink link = new BookingLink(organizationId, ownerId, "Office Hours", durationMinutes, "office-hours");
        link.setId(id);
        return link;
    }

    private BookingSlot slot(UUID id, UUID linkId) {
        Instant startAt = Instant.parse("2026-09-01T15:00:00Z");
        BookingSlot slot = new BookingSlot(linkId, startAt, startAt.plusSeconds(1800));
        slot.setId(id);
        return slot;
    }
}
