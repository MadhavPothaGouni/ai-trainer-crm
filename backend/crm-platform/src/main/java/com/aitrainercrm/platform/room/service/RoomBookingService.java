package com.aitrainercrm.platform.room.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.room.dto.CreateRoomBookingRequest;
import com.aitrainercrm.platform.room.dto.UpdateRoomBookingRequest;
import com.aitrainercrm.platform.room.entity.RoomBooking;
import com.aitrainercrm.platform.room.repository.RoomBookingRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One reservation of a {@link com.aitrainercrm.platform.room.entity.Room} for a block of time -
 * see {@link RoomBooking}'s javadoc and V53's migration comment for the backstory. Follows the
 * same shape as {@code LockerAssignmentService}: OWN/TEAM/DEPARTMENT/ORGANIZATION record-level
 * authorization via {@link ScopeAuthorizationService}, {@code resolveOwner} defaulting a null
 * {@code ownerId} to the caller. What's new here is {@link #assertNoOverlap}, a real scheduling-
 * conflict rule checked before a booking is created, before its time window is edited, and before
 * it's re-confirmed after being cancelled - mirroring the business-rule-checked-create pattern
 * {@code PromoRedemptionService#assertRedeemable} established (typed {@link BusinessException},
 * one distinct error code per rejection reason - here there's only one rule to check).
 */
@Service
@RequiredArgsConstructor
public class RoomBookingService {

    private static final Permission.Resource RESOURCE = Permission.Resource.ROOM_BOOKING;

    private final RoomBookingRepository roomBookingRepository;
    private final RoomService roomService;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<RoomBooking> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleOwnerIds
                .map(ownerIds -> roomBookingRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(principal.getOrganizationId(), ownerIds, pageable))
                .orElseGet(() -> roomBookingRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable));
    }

    @Transactional(readOnly = true)
    public RoomBooking get(UserPrincipal principal, UUID roomBookingId) {
        RoomBooking booking = findOrThrow(principal.getOrganizationId(), roomBookingId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, booking.getOwnerId());
        return booking;
    }

    @Transactional
    public RoomBooking create(UserPrincipal principal, CreateRoomBookingRequest request) {
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, request.ownerId());
        roomService.findOrThrow(principal.getOrganizationId(), request.roomId());
        assertValidWindow(request.startsAt(), request.endsAt());
        assertNoOverlap(request.roomId(), request.startsAt(), request.endsAt(), null);

        RoomBooking booking = new RoomBooking(
                principal.getOrganizationId(), request.roomId(), ownerId, request.purpose(), request.startsAt(), request.endsAt());
        booking.setNotes(request.notes());
        roomBookingRepository.save(booking);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "RoomBooking", booking.getId()));
        return booking;
    }

    @Transactional
    public RoomBooking update(UserPrincipal principal, UUID roomBookingId, UpdateRoomBookingRequest request) {
        RoomBooking booking = findOrThrow(principal.getOrganizationId(), roomBookingId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, booking.getOwnerId());

        assertValidWindow(request.startsAt(), request.endsAt());
        if (booking.getStatus() == RoomBooking.Status.CONFIRMED) {
            assertNoOverlap(booking.getRoomId(), request.startsAt(), request.endsAt(), booking.getId());
        }

        booking.setPurpose(request.purpose());
        booking.setStartsAt(request.startsAt());
        booking.setEndsAt(request.endsAt());
        booking.setNotes(request.notes());
        roomBookingRepository.save(booking);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "RoomBooking", booking.getId()));
        return booking;
    }

    /**
     * No invalid-transition checks - re-confirming a cancelled booking is a legitimate
     * correction, same restraint every other status machine in this platform documents. Moving
     * *to* CONFIRMED re-checks {@link #assertNoOverlap}, since the room's schedule may have
     * filled in while this booking sat cancelled; moving *away* from CONFIRMED never needs the
     * check.
     */
    @Transactional
    public RoomBooking updateStatus(UserPrincipal principal, UUID roomBookingId, RoomBooking.Status newStatus) {
        RoomBooking booking = findOrThrow(principal.getOrganizationId(), roomBookingId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, booking.getOwnerId());

        if (newStatus == RoomBooking.Status.CONFIRMED && booking.getStatus() != RoomBooking.Status.CONFIRMED) {
            assertNoOverlap(booking.getRoomId(), booking.getStartsAt(), booking.getEndsAt(), booking.getId());
        }
        booking.setStatus(newStatus);
        roomBookingRepository.save(booking);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "RoomBooking", booking.getId()));
        return booking;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID roomBookingId) {
        RoomBooking booking = findOrThrow(principal.getOrganizationId(), roomBookingId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, booking.getOwnerId());

        booking.setDeletedAt(Instant.now());
        roomBookingRepository.save(booking);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "RoomBooking", roomBookingId));
    }

    private void assertValidWindow(Instant startsAt, Instant endsAt) {
        if (!startsAt.isBefore(endsAt)) {
            throw new BusinessException("ROOM_BOOKING_INVALID_WINDOW", "The booking's end time must be after its start time", HttpStatus.BAD_REQUEST);
        }
    }

    /** A room can't hold two CONFIRMED bookings with overlapping [startsAt, endsAt) windows - see this class's javadoc. */
    private void assertNoOverlap(UUID roomId, Instant startsAt, Instant endsAt, UUID excludeBookingId) {
        boolean overlaps = excludeBookingId == null
                ? roomBookingRepository.existsByRoomIdAndStatusAndDeletedAtIsNullAndStartsAtLessThanAndEndsAtGreaterThan(
                        roomId, RoomBooking.Status.CONFIRMED, endsAt, startsAt)
                : roomBookingRepository.existsByRoomIdAndStatusAndDeletedAtIsNullAndIdNotAndStartsAtLessThanAndEndsAtGreaterThan(
                        roomId, RoomBooking.Status.CONFIRMED, excludeBookingId, endsAt, startsAt);
        if (overlaps) {
            throw new BusinessException("ROOM_BOOKING_CONFLICT", "This room is already booked for an overlapping time slot", HttpStatus.CONFLICT);
        }
    }

    private RoomBooking findOrThrow(UUID organizationId, UUID roomBookingId) {
        return roomBookingRepository.findActiveByIdAndOrganizationId(roomBookingId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("RoomBooking", roomBookingId));
    }

    private UUID resolveOwner(UserPrincipal principal, Permission.Action action, UUID requestedOwnerId) {
        if (requestedOwnerId == null || requestedOwnerId.equals(principal.getId())) {
            return principal.getId();
        }
        if (scopeAuthorizationService.highestGranted(principal, RESOURCE, action) != ScopeAuthorizationService.Access.ORGANIZATION) {
            throw new ForbiddenException("You can only " + action.name().toLowerCase(Locale.ROOT) + " bookings made by yourself");
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
