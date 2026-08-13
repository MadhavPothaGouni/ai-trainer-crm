package com.aitrainercrm.platform.sequence.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.DuplicateResourceException;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.lead.repository.LeadRepository;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.sequence.dto.CreateSequenceEnrollmentRequest;
import com.aitrainercrm.platform.sequence.entity.Sequence;
import com.aitrainercrm.platform.sequence.entity.SequenceEnrollment;
import com.aitrainercrm.platform.sequence.entity.SequenceStep;
import com.aitrainercrm.platform.sequence.repository.SequenceEnrollmentRepository;
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
 * A Lead/Contact being worked through a {@link Sequence} by a rep. Same shape {@code
 * TicketService}/{@code CourseEnrollmentService} use: OWN/TEAM/DEPARTMENT/ORGANIZATION record-level
 * authorization via {@link ScopeAuthorizationService}, {@link #resolveOwner} defaulting a null
 * {@code ownerId} to the caller. See {@link SequenceEnrollment}'s javadoc for why {@code ownerId}
 * and {@code targetId} are two different people here, unlike CourseEnrollment/UserCertification.
 */
@Service
@RequiredArgsConstructor
public class SequenceEnrollmentService {

    private static final Permission.Resource RESOURCE = Permission.Resource.SEQUENCE_ENROLLMENT;

    private final SequenceEnrollmentRepository sequenceEnrollmentRepository;
    private final SequenceService sequenceService;
    private final LeadRepository leadRepository;
    private final ContactRepository contactRepository;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<SequenceEnrollment> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleOwnerIds
                .map(ownerIds -> sequenceEnrollmentRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNullOrderByEnrolledAtDesc(
                        principal.getOrganizationId(), ownerIds, pageable))
                .orElseGet(() -> sequenceEnrollmentRepository.findByOrganizationIdAndDeletedAtIsNullOrderByEnrolledAtDesc(
                        principal.getOrganizationId(), pageable));
    }

    @Transactional(readOnly = true)
    public SequenceEnrollment get(UserPrincipal principal, UUID enrollmentId) {
        SequenceEnrollment enrollment = findOrThrow(principal.getOrganizationId(), enrollmentId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, enrollment.getOwnerId());
        return enrollment;
    }

    @Transactional
    public SequenceEnrollment create(UserPrincipal principal, CreateSequenceEnrollmentRequest request) {
        Sequence sequence = sequenceService.findOrThrow(principal.getOrganizationId(), request.sequenceId());
        assertTargetInOrganization(principal.getOrganizationId(), request.targetType(), request.targetId());
        UUID ownerId = resolveOwner(principal, request.ownerId());

        if (sequenceEnrollmentRepository.existsByOrganizationIdAndSequenceIdAndTargetTypeAndTargetIdAndDeletedAtIsNull(
                principal.getOrganizationId(), sequence.getId(), request.targetType(), request.targetId())) {
            throw new DuplicateResourceException("This record is already actively enrolled in this sequence");
        }

        SequenceEnrollment enrollment = new SequenceEnrollment(
                principal.getOrganizationId(), sequence.getId(), request.targetType(), request.targetId(), ownerId);
        sequenceEnrollmentRepository.save(enrollment);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "SequenceEnrollment", enrollment.getId()));
        return enrollment;
    }

    /**
     * Moves the enrollment to its next step. Walking off the end of the sequence's step list
     * automatically completes the enrollment (stamping {@code completedAt}) rather than requiring a
     * separate "mark complete" call - there's nothing left to advance to, so "one past the last step"
     * and "done" are the same state. Only legal from {@code ACTIVE}; a paused or cancelled enrollment
     * has to be reactivated (via the status endpoint) before it can advance again.
     */
    @Transactional
    public SequenceEnrollment advance(UserPrincipal principal, UUID enrollmentId) {
        SequenceEnrollment enrollment = findOrThrow(principal.getOrganizationId(), enrollmentId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, enrollment.getOwnerId());
        if (enrollment.getStatus() != SequenceEnrollment.Status.ACTIVE) {
            throw new BusinessException(
                    "SEQUENCE_ENROLLMENT_NOT_ACTIVE", "Only an active enrollment can advance to its next step", HttpStatus.CONFLICT);
        }

        List<SequenceStep> steps = sequenceService.stepsOf(enrollment.getSequenceId());
        int nextIndex = enrollment.getCurrentStepIndex() + 1;
        enrollment.setCurrentStepIndex(nextIndex);
        if (nextIndex >= steps.size()) {
            enrollment.setStatus(SequenceEnrollment.Status.COMPLETED);
            enrollment.setCompletedAt(Instant.now());
        }
        sequenceEnrollmentRepository.save(enrollment);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "SequenceEnrollment", enrollment.getId()));
        return enrollment;
    }

    /** ACTIVE/PAUSED/CANCELLED only - see UpdateSequenceEnrollmentStatusRequest's javadoc for why COMPLETED never comes through here. */
    @Transactional
    public SequenceEnrollment updateStatus(UserPrincipal principal, UUID enrollmentId, SequenceEnrollment.Status status) {
        SequenceEnrollment enrollment = findOrThrow(principal.getOrganizationId(), enrollmentId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, enrollment.getOwnerId());
        if (status == SequenceEnrollment.Status.COMPLETED) {
            throw new BusinessException(
                    "SEQUENCE_ENROLLMENT_INVALID_STATUS", "COMPLETED is only reached by advancing past the last step", HttpStatus.BAD_REQUEST);
        }

        enrollment.setStatus(status);
        sequenceEnrollmentRepository.save(enrollment);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "SequenceEnrollment", enrollment.getId()));
        return enrollment;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID enrollmentId) {
        SequenceEnrollment enrollment = findOrThrow(principal.getOrganizationId(), enrollmentId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, enrollment.getOwnerId());

        enrollment.setDeletedAt(Instant.now());
        sequenceEnrollmentRepository.save(enrollment);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "SequenceEnrollment", enrollmentId));
    }

    private SequenceEnrollment findOrThrow(UUID organizationId, UUID enrollmentId) {
        return sequenceEnrollmentRepository.findActiveByIdAndOrganizationId(enrollmentId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("SequenceEnrollment", enrollmentId));
    }

    private UUID resolveOwner(UserPrincipal principal, UUID requestedOwnerId) {
        if (requestedOwnerId == null || requestedOwnerId.equals(principal.getId())) {
            return principal.getId();
        }
        if (scopeAuthorizationService.highestGranted(principal, RESOURCE, Permission.Action.CREATE) != ScopeAuthorizationService.Access.ORGANIZATION) {
            throw new ForbiddenException("You can only enroll targets you own yourself");
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

    private void assertTargetInOrganization(UUID organizationId, SequenceEnrollment.TargetType targetType, UUID targetId) {
        boolean exists = switch (targetType) {
            case LEAD -> leadRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(targetId, organizationId);
            case CONTACT -> contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(targetId, organizationId);
        };
        if (!exists) {
            throw new ResourceNotFoundException(targetType.name().toLowerCase(Locale.ROOT), targetId);
        }
    }
}
