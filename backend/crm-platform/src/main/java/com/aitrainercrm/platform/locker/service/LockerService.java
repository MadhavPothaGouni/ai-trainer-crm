package com.aitrainercrm.platform.locker.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.locker.dto.CreateLockerRequest;
import com.aitrainercrm.platform.locker.dto.UpdateLockerRequest;
import com.aitrainercrm.platform.locker.entity.Locker;
import com.aitrainercrm.platform.locker.repository.LockerRepository;
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
 * The locker catalog. Exactly {@link com.aitrainercrm.platform.vendor.service.VendorService}'s
 * shape - no {@link com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService}
 * calls here, since lockers have no {@code ownerId} (see {@link Locker}'s javadoc); the
 * controller's {@code @PreAuthorize} (any of TEAM/DEPARTMENT/ORGANIZATION) is the whole
 * authorization story. {@link #findOrThrow} is package-private so {@code LockerAssignmentService}
 * can reuse it when validating a new assignment's parent locker.
 */
@Service
@RequiredArgsConstructor
public class LockerService {

    private final LockerRepository lockerRepository;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<Locker> list(UserPrincipal principal, Pageable pageable) {
        return lockerRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable);
    }

    @Transactional(readOnly = true)
    public Locker get(UserPrincipal principal, UUID lockerId) {
        return findOrThrow(principal.getOrganizationId(), lockerId);
    }

    @Transactional
    public Locker create(UserPrincipal principal, CreateLockerRequest request) {
        Locker locker = new Locker(principal.getOrganizationId(), request.label());
        locker.setLocation(request.location());
        locker.setSize(request.size());
        locker.setNotes(request.notes());
        lockerRepository.save(locker);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "Locker", locker.getId()));
        return locker;
    }

    @Transactional
    public Locker update(UserPrincipal principal, UUID lockerId, UpdateLockerRequest request) {
        Locker locker = findOrThrow(principal.getOrganizationId(), lockerId);
        locker.setLabel(request.label());
        locker.setLocation(request.location());
        locker.setSize(request.size());
        locker.setStatus(request.status());
        locker.setNotes(request.notes());
        lockerRepository.save(locker);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Locker", locker.getId()));
        return locker;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID lockerId) {
        Locker locker = findOrThrow(principal.getOrganizationId(), lockerId);
        locker.setDeletedAt(Instant.now());
        lockerRepository.save(locker);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "Locker", lockerId));
    }

    Locker findOrThrow(UUID organizationId, UUID lockerId) {
        return lockerRepository.findActiveByIdAndOrganizationId(lockerId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Locker", lockerId));
    }
}
