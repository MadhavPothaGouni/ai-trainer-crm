package com.aitrainercrm.platform.groupclass.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.groupclass.dto.CreateClassAttendanceRequest;
import com.aitrainercrm.platform.groupclass.dto.UpdateClassAttendanceRequest;
import com.aitrainercrm.platform.groupclass.entity.ClassAttendance;
import com.aitrainercrm.platform.groupclass.entity.ClassSession;
import com.aitrainercrm.platform.groupclass.entity.GroupClass;
import com.aitrainercrm.platform.groupclass.repository.ClassAttendanceRepository;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import java.time.Instant;
import java.util.List;
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
 * A contact's registration on a {@link ClassSession}'s roster - see {@link ClassAttendance}'s
 * javadoc for why {@code ownerId} is copied from the parent session rather than independently
 * resolved. The one piece of real cross-record business logic in this module: {@link #create}
 * enforces the session's capacity (its own {@code capacityOverride}, falling back to the parent
 * {@link GroupClass}'s {@code capacity}; null on both means unlimited) by rejecting a new
 * registration once the active roster (REGISTERED + ATTENDED) is full, throwing a
 * {@link BusinessException} mapped to 409 CONFLICT rather than the usual 400/403/404 this
 * platform's other exceptions map to.
 */
@Service
@RequiredArgsConstructor
public class ClassAttendanceService {

    private static final Permission.Resource RESOURCE = Permission.Resource.CLASS_ATTENDANCE;
    private static final List<ClassAttendance.Status> ACTIVE_ROSTER_STATUSES = List.of(ClassAttendance.Status.REGISTERED, ClassAttendance.Status.ATTENDED);

    private final ClassAttendanceRepository classAttendanceRepository;
    private final ClassSessionService classSessionService;
    private final GroupClassService groupClassService;
    private final ContactRepository contactRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<ClassAttendance> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleOwnerIds
                .map(ownerIds -> classAttendanceRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(principal.getOrganizationId(), ownerIds, pageable))
                .orElseGet(() -> classAttendanceRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable));
    }

    @Transactional(readOnly = true)
    public ClassAttendance get(UserPrincipal principal, UUID classAttendanceId) {
        ClassAttendance attendance = findOrThrow(principal.getOrganizationId(), classAttendanceId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, attendance.getOwnerId());
        return attendance;
    }

    @Transactional
    public ClassAttendance create(UserPrincipal principal, CreateClassAttendanceRequest request) {
        ClassSession session = classSessionService.findOrThrow(principal.getOrganizationId(), request.classSessionId());
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.CREATE, session.getOwnerId());
        assertContactInOrganization(principal.getOrganizationId(), request.contactId());
        assertHasCapacity(session);

        ClassAttendance attendance = new ClassAttendance(principal.getOrganizationId(), session.getId(), request.contactId(), session.getOwnerId());
        attendance.setNotes(request.notes());
        classAttendanceRepository.save(attendance);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "ClassAttendance", attendance.getId()));
        return attendance;
    }

    @Transactional
    public ClassAttendance update(UserPrincipal principal, UUID classAttendanceId, UpdateClassAttendanceRequest request) {
        ClassAttendance attendance = findOrThrow(principal.getOrganizationId(), classAttendanceId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, attendance.getOwnerId());

        attendance.setNotes(request.notes());
        classAttendanceRepository.save(attendance);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "ClassAttendance", attendance.getId()));
        return attendance;
    }

    /**
     * No invalid-transition checks - correcting a mistaken NO_SHOW back to ATTENDED, or a
     * cancelled registration back to REGISTERED, is normal. {@code checkedInAt} is stamped only
     * the first time status moves to ATTENDED (never overwritten on subsequent moves), the same
     * "stamp once" rule {@code Contract#signedAt}/{@code ClientGoal#achievedAt} use - see this
     * class's javadoc.
     */
    @Transactional
    public ClassAttendance updateStatus(UserPrincipal principal, UUID classAttendanceId, ClassAttendance.Status newStatus) {
        ClassAttendance attendance = findOrThrow(principal.getOrganizationId(), classAttendanceId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, attendance.getOwnerId());

        if (newStatus == ClassAttendance.Status.ATTENDED && attendance.getCheckedInAt() == null) {
            attendance.setCheckedInAt(Instant.now());
        }
        attendance.setStatus(newStatus);
        classAttendanceRepository.save(attendance);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "ClassAttendance", attendance.getId()));
        return attendance;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID classAttendanceId) {
        ClassAttendance attendance = findOrThrow(principal.getOrganizationId(), classAttendanceId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, attendance.getOwnerId());

        attendance.setDeletedAt(Instant.now());
        classAttendanceRepository.save(attendance);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "ClassAttendance", classAttendanceId));
    }

    private ClassAttendance findOrThrow(UUID organizationId, UUID classAttendanceId) {
        return classAttendanceRepository.findActiveByIdAndOrganizationId(classAttendanceId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("ClassAttendance", classAttendanceId));
    }

    private void assertContactInOrganization(UUID organizationId, UUID contactId) {
        if (!contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(contactId, organizationId)) {
            throw new ResourceNotFoundException("Contact", contactId);
        }
    }

    private void assertHasCapacity(ClassSession session) {
        Integer capacity = session.getCapacityOverride();
        if (capacity == null) {
            GroupClass groupClass = groupClassService.findOrThrow(session.getOrganizationId(), session.getGroupClassId());
            capacity = groupClass.getCapacity();
        }
        if (capacity == null) {
            return;
        }
        long activeRosterSize = classAttendanceRepository.countByClassSessionIdAndDeletedAtIsNullAndStatusIn(session.getId(), ACTIVE_ROSTER_STATUSES);
        if (activeRosterSize >= capacity) {
            throw new BusinessException("CLASS_SESSION_FULL", "This class session is full", HttpStatus.CONFLICT);
        }
    }
}
