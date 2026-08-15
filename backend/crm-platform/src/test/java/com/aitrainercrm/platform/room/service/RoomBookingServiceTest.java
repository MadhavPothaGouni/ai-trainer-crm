package com.aitrainercrm.platform.room.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.room.dto.CreateRoomBookingRequest;
import com.aitrainercrm.platform.room.dto.UpdateRoomBookingRequest;
import com.aitrainercrm.platform.room.entity.RoomBooking;
import com.aitrainercrm.platform.room.repository.RoomBookingRepository;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** See {@link RoomBookingService}'s javadoc for the overlap-conflict rule this mostly exists to cover. */
@ExtendWith(MockitoExtension.class)
class RoomBookingServiceTest {

    @Mock private RoomBookingRepository roomBookingRepository;
    @Mock private RoomService roomService;
    @Mock private UserRepository userRepository;
    @Mock private ScopeAuthorizationService scopeAuthorizationService;
    @Mock private ApplicationEventPublisher events;

    private RoomBookingService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID roomId = UUID.randomUUID();

    private final Instant startsAt = Instant.now().plus(1, ChronoUnit.DAYS);
    private final Instant endsAt = startsAt.plus(1, ChronoUnit.HOURS);

    @BeforeEach
    void setUp() {
        service = new RoomBookingService(roomBookingRepository, roomService, userRepository, scopeAuthorizationService, events);
    }

    private UserPrincipal principal() {
        return new UserPrincipal(callerId, "staff@example.com", organizationId, List.of());
    }

    @Test
    void create_noOverlap_succeeds() {
        when(roomBookingRepository.existsByRoomIdAndStatusAndDeletedAtIsNullAndStartsAtLessThanAndEndsAtGreaterThan(
                        eq(roomId), eq(RoomBooking.Status.CONFIRMED), any(), any()))
                .thenReturn(false);

        RoomBooking booking = service.create(principal(), new CreateRoomBookingRequest(roomId, "1:1 session", startsAt, endsAt, null, null));

        assertThat(booking.getStatus()).isEqualTo(RoomBooking.Status.CONFIRMED);
        assertThat(booking.getOwnerId()).isEqualTo(callerId);
    }

    @Test
    void create_overlappingConfirmedBookingExists_throwsConflict() {
        when(roomBookingRepository.existsByRoomIdAndStatusAndDeletedAtIsNullAndStartsAtLessThanAndEndsAtGreaterThan(
                        eq(roomId), eq(RoomBooking.Status.CONFIRMED), any(), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(principal(), new CreateRoomBookingRequest(roomId, "1:1 session", startsAt, endsAt, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("ROOM_BOOKING_CONFLICT"));
    }

    @Test
    void create_endBeforeStart_throwsInvalidWindow() {
        assertThatThrownBy(() -> service.create(principal(), new CreateRoomBookingRequest(roomId, "1:1 session", endsAt, startsAt, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("ROOM_BOOKING_INVALID_WINDOW"));
    }

    @Test
    void update_movingTimeIntoAnotherConfirmedBookingsWindow_throwsConflict() {
        UUID bookingId = UUID.randomUUID();
        RoomBooking booking = new RoomBooking(organizationId, roomId, callerId, "Original", startsAt, endsAt);
        booking.setId(bookingId);
        when(roomBookingRepository.findActiveByIdAndOrganizationId(bookingId, organizationId)).thenReturn(Optional.of(booking));
        when(roomBookingRepository.existsByRoomIdAndStatusAndDeletedAtIsNullAndIdNotAndStartsAtLessThanAndEndsAtGreaterThan(
                        eq(roomId), eq(RoomBooking.Status.CONFIRMED), eq(bookingId), any(), any()))
                .thenReturn(true);

        Instant newStartsAt = startsAt.plus(30, ChronoUnit.MINUTES);
        Instant newEndsAt = newStartsAt.plus(1, ChronoUnit.HOURS);

        assertThatThrownBy(() -> service.update(principal(), bookingId, new UpdateRoomBookingRequest("Moved", newStartsAt, newEndsAt, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("ROOM_BOOKING_CONFLICT"));
    }

    @Test
    void updateStatus_reconfirmingACancelledBooking_reChecksOverlap() {
        UUID bookingId = UUID.randomUUID();
        RoomBooking booking = new RoomBooking(organizationId, roomId, callerId, "Original", startsAt, endsAt);
        booking.setId(bookingId);
        booking.setStatus(RoomBooking.Status.CANCELLED);
        when(roomBookingRepository.findActiveByIdAndOrganizationId(bookingId, organizationId)).thenReturn(Optional.of(booking));
        when(roomBookingRepository.existsByRoomIdAndStatusAndDeletedAtIsNullAndIdNotAndStartsAtLessThanAndEndsAtGreaterThan(
                        eq(roomId), eq(RoomBooking.Status.CONFIRMED), eq(bookingId), any(), any()))
                .thenReturn(false);

        RoomBooking reconfirmed = service.updateStatus(principal(), bookingId, RoomBooking.Status.CONFIRMED);

        assertThat(reconfirmed.getStatus()).isEqualTo(RoomBooking.Status.CONFIRMED);
    }
}
