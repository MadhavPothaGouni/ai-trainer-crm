package com.aitrainercrm.platform.equipment.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.equipment.dto.CreateEquipmentReservationRequest;
import com.aitrainercrm.platform.equipment.dto.UpdateEquipmentReservationRequest;
import com.aitrainercrm.platform.equipment.entity.EquipmentReservation;
import com.aitrainercrm.platform.equipment.repository.EquipmentReservationRepository;
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
 * A booking of a specific {@link com.aitrainercrm.platform.equipment.entity.Equipment} for a time
 * slot - see {@link EquipmentReservation}'s javadoc and V56's migration comment for the backstory.
 * Lives in this package (rather than its own {@code equipmentreservation} package, the way most
 * new occurrence entities in this platform get their own package) specifically so it can reuse
 * {@link EquipmentService#findOrThrow}, which is package-private - same reasoning
 * {@code MaintenanceLogService} already established for reusing that method. Otherwise follows the
 * same OWN/TEAM/DEPARTMENT/ORGANIZATION record-level authorization shape as
 * {@code LockerAssignmentService}, with {@code resolveOwner} defaulting a null {@code ownerId} to
 * the caller.
 */
@Service
@RequiredArgsConstructor
public class EquipmentReservationService {

    private static final Permission.Resource RESOURCE = Permission.Resource.EQUIPMENT_RESERVATION;

    private final EquipmentReservationRepository equipmentReservationRepository;
    private final EquipmentService equipmentService;
    private final ContactRepository contactRepository;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<EquipmentReservation> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleOwnerIds
                .map(ownerIds -> equipmentReservationRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(principal.getOrganizationId(), ownerIds, pageable))
                .orElseGet(() -> equipmentReservationRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable));
    }

    @Transactional(readOnly = true)
    public EquipmentReservation get(UserPrincipal principal, UUID equipmentReservationId) {
        EquipmentReservation reservation = findOrThrow(principal.getOrganizationId(), equipmentReservationId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, reservation.getOwnerId());
        return reservation;
    }

    @Transactional
    public EquipmentReservation create(UserPrincipal principal, CreateEquipmentReservationRequest request) {
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, request.ownerId());
        equipmentService.findOrThrow(principal.getOrganizationId(), request.equipmentId());
        assertContactExistsIfProvided(principal.getOrganizationId(), request.contactId());

        EquipmentReservation reservation = new EquipmentReservation(
                principal.getOrganizationId(), request.equipmentId(), ownerId, request.startsAt(), request.endsAt());
        reservation.setContactId(request.contactId());
        reservation.setNotes(request.notes());
        equipmentReservationRepository.save(reservation);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "EquipmentReservation", reservation.getId()));
        return reservation;
    }

    @Transactional
    public EquipmentReservation update(UserPrincipal principal, UUID equipmentReservationId, UpdateEquipmentReservationRequest request) {
        EquipmentReservation reservation = findOrThrow(principal.getOrganizationId(), equipmentReservationId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, reservation.getOwnerId());
        assertContactExistsIfProvided(principal.getOrganizationId(), request.contactId());

        reservation.setContactId(request.contactId());
        reservation.setStartsAt(request.startsAt());
        reservation.setEndsAt(request.endsAt());
        reservation.setNotes(request.notes());
        equipmentReservationRepository.save(reservation);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "EquipmentReservation", reservation.getId()));
        return reservation;
    }

    /** No invalid-transition checks - re-confirming a cancelled reservation is a legitimate correction, same restraint every other status machine in this platform documents. */
    @Transactional
    public EquipmentReservation updateStatus(UserPrincipal principal, UUID equipmentReservationId, EquipmentReservation.Status newStatus) {
        EquipmentReservation reservation = findOrThrow(principal.getOrganizationId(), equipmentReservationId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, reservation.getOwnerId());

        reservation.setStatus(newStatus);
        equipmentReservationRepository.save(reservation);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "EquipmentReservation", reservation.getId()));
        return reservation;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID equipmentReservationId) {
        EquipmentReservation reservation = findOrThrow(principal.getOrganizationId(), equipmentReservationId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, reservation.getOwnerId());

        reservation.setDeletedAt(Instant.now());
        equipmentReservationRepository.save(reservation);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "EquipmentReservation", equipmentReservationId));
    }

    private void assertContactExistsIfProvided(UUID organizationId, UUID contactId) {
        if (contactId != null && !contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(contactId, organizationId)) {
            throw new ResourceNotFoundException("Contact", contactId);
        }
    }

    private EquipmentReservation findOrThrow(UUID organizationId, UUID equipmentReservationId) {
        return equipmentReservationRepository.findActiveByIdAndOrganizationId(equipmentReservationId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("EquipmentReservation", equipmentReservationId));
    }

    private UUID resolveOwner(UserPrincipal principal, Permission.Action action, UUID requestedOwnerId) {
        if (requestedOwnerId == null || requestedOwnerId.equals(principal.getId())) {
            return principal.getId();
        }
        if (scopeAuthorizationService.highestGranted(principal, RESOURCE, action) != ScopeAuthorizationService.Access.ORGANIZATION) {
            throw new ForbiddenException("You can only " + action.name().toLowerCase(Locale.ROOT) + " reservations made by yourself");
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
