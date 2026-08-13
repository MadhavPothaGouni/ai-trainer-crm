package com.aitrainercrm.platform.trainingsession.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.booking.entity.BookingLink;
import com.aitrainercrm.platform.booking.entity.BookingSlot;
import com.aitrainercrm.platform.booking.repository.BookingLinkRepository;
import com.aitrainercrm.platform.booking.repository.BookingSlotRepository;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.trainingsession.dto.CreateTrainingSessionRequest;
import com.aitrainercrm.platform.trainingsession.entity.TrainingSession;
import com.aitrainercrm.platform.trainingsession.repository.TrainingSessionRepository;
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

/** See {@link TrainingSessionService}'s javadoc for the shape this mirrors ({@code ClientGoalService}/{@code ContractService}). */
@ExtendWith(MockitoExtension.class)
class TrainingSessionServiceTest {

    @Mock private TrainingSessionRepository trainingSessionRepository;
    @Mock private ContactRepository contactRepository;
    @Mock private BookingSlotRepository bookingSlotRepository;
    @Mock private BookingLinkRepository bookingLinkRepository;
    @Mock private UserRepository userRepository;
    @Mock private ScopeAuthorizationService scopeAuthorizationService;
    @Mock private ApplicationEventPublisher events;

    private TrainingSessionService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID contactId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TrainingSessionService(
                trainingSessionRepository, contactRepository, bookingSlotRepository, bookingLinkRepository, userRepository,
                scopeAuthorizationService, events);
    }

    private UserPrincipal principal(UUID userId) {
        return new UserPrincipal(userId, "coach@example.com", organizationId, List.of());
    }

    private CreateTrainingSessionRequest createRequest(UUID bookingSlotId, UUID ownerId) {
        return new CreateTrainingSessionRequest(
                contactId, bookingSlotId, Instant.parse("2026-09-01T15:00:00Z"), 45, TrainingSession.SessionType.IN_PERSON,
                "Lower body", 7, "Great effort today", ownerId);
    }

    @Test
    void create_noOwnerIdRequested_selfAssignsTheCaller() {
        when(contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(contactId, organizationId)).thenReturn(true);

        TrainingSession result = service.create(principal(callerId), createRequest(null, null));

        assertThat(result.getOwnerId()).isEqualTo(callerId);
        assertThat(result.getContactId()).isEqualTo(contactId);
        assertThat(result.getStatus()).isEqualTo(TrainingSession.Status.SCHEDULED);
        verify(trainingSessionRepository).save(result);
    }

    @Test
    void create_assigningSomeoneElseWithoutOrganizationScope_isForbidden() {
        UUID otherUserId = UUID.randomUUID();
        when(scopeAuthorizationService.highestGranted(any(), any(), any())).thenReturn(ScopeAuthorizationService.Access.TEAM);

        assertThatThrownBy(() -> service.create(principal(callerId), createRequest(null, otherUserId))).isInstanceOf(ForbiddenException.class);
        verify(trainingSessionRepository, never()).save(any());
    }

    @Test
    void create_bookingSlotFromAnotherOrganization_isRejected() {
        UUID bookingSlotId = UUID.randomUUID();
        UUID bookingLinkId = UUID.randomUUID();
        when(contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(contactId, organizationId)).thenReturn(true);
        BookingSlot slot = new BookingSlot(bookingLinkId, Instant.now(), Instant.now().plusSeconds(1800));
        slot.setId(bookingSlotId);
        when(bookingSlotRepository.findById(bookingSlotId)).thenReturn(Optional.of(slot));
        when(bookingLinkRepository.findActiveByIdAndOrganizationId(bookingLinkId, organizationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(principal(callerId), createRequest(bookingSlotId, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(trainingSessionRepository, never()).save(any());
    }

    @Test
    void create_bookingSlotInSameOrganization_isAccepted() {
        UUID bookingSlotId = UUID.randomUUID();
        UUID bookingLinkId = UUID.randomUUID();
        when(contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(contactId, organizationId)).thenReturn(true);
        BookingSlot slot = new BookingSlot(bookingLinkId, Instant.now(), Instant.now().plusSeconds(1800));
        slot.setId(bookingSlotId);
        when(bookingSlotRepository.findById(bookingSlotId)).thenReturn(Optional.of(slot));
        BookingLink link = new BookingLink(organizationId, callerId, "Office Hours", 30, "office-hours");
        link.setId(bookingLinkId);
        when(bookingLinkRepository.findActiveByIdAndOrganizationId(bookingLinkId, organizationId)).thenReturn(Optional.of(link));

        TrainingSession result = service.create(principal(callerId), createRequest(bookingSlotId, null));

        assertThat(result.getBookingSlotId()).isEqualTo(bookingSlotId);
    }

    @Test
    void updateStatus_movingToNoShowAndBackToScheduled_isAllowed() {
        UUID sessionId = UUID.randomUUID();
        TrainingSession session = new TrainingSession(organizationId, contactId, callerId, Instant.now());
        session.setId(sessionId);
        when(trainingSessionRepository.findActiveByIdAndOrganizationId(sessionId, organizationId)).thenReturn(Optional.of(session));

        TrainingSession result = service.updateStatus(principal(callerId), sessionId, TrainingSession.Status.NO_SHOW);
        assertThat(result.getStatus()).isEqualTo(TrainingSession.Status.NO_SHOW);

        TrainingSession corrected = service.updateStatus(principal(callerId), sessionId, TrainingSession.Status.SCHEDULED);
        assertThat(corrected.getStatus()).isEqualTo(TrainingSession.Status.SCHEDULED);
    }
}
