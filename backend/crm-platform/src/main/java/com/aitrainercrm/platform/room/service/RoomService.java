package com.aitrainercrm.platform.room.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.room.dto.CreateRoomRequest;
import com.aitrainercrm.platform.room.dto.UpdateRoomRequest;
import com.aitrainercrm.platform.room.entity.Room;
import com.aitrainercrm.platform.room.repository.RoomRepository;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The bookable-room catalog. Exactly {@link com.aitrainercrm.platform.locker.service.LockerService}'s
 * shape - no {@link com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService}
 * calls here, since rooms have no {@code ownerId} (see {@link Room}'s javadoc); the controller's
 * {@code @PreAuthorize} (any of TEAM/DEPARTMENT/ORGANIZATION) is the whole authorization story.
 * {@link #findOrThrow} is package-private so {@code RoomBookingService} can reuse it when
 * validating a new booking's parent room.
 */
@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<Room> list(UserPrincipal principal, Pageable pageable) {
        return roomRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable);
    }

    @Transactional(readOnly = true)
    public Room get(UserPrincipal principal, UUID roomId) {
        return findOrThrow(principal.getOrganizationId(), roomId);
    }

    @Transactional
    public Room create(UserPrincipal principal, CreateRoomRequest request) {
        Room room = new Room(principal.getOrganizationId(), request.label());
        room.setLocation(request.location());
        room.setCapacity(request.capacity());
        room.setNotes(request.notes());
        roomRepository.save(room);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "Room", room.getId()));
        return room;
    }

    @Transactional
    public Room update(UserPrincipal principal, UUID roomId, UpdateRoomRequest request) {
        Room room = findOrThrow(principal.getOrganizationId(), roomId);
        room.setLabel(request.label());
        room.setLocation(request.location());
        room.setCapacity(request.capacity());
        room.setStatus(request.status());
        room.setNotes(request.notes());
        roomRepository.save(room);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Room", room.getId()));
        return room;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID roomId) {
        Room room = findOrThrow(principal.getOrganizationId(), roomId);
        room.setDeletedAt(Instant.now());
        roomRepository.save(room);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "Room", roomId));
    }

    Room findOrThrow(UUID organizationId, UUID roomId) {
        return roomRepository.findActiveByIdAndOrganizationId(roomId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Room", roomId));
    }
}
