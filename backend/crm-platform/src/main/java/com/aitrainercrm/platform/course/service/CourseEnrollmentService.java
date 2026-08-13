package com.aitrainercrm.platform.course.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.DuplicateResourceException;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.course.dto.CreateCourseEnrollmentRequest;
import com.aitrainercrm.platform.course.entity.Course;
import com.aitrainercrm.platform.course.entity.CourseEnrollment;
import com.aitrainercrm.platform.course.repository.CourseEnrollmentRepository;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.time.Instant;
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
 * A learner's enrollment in one {@link Course}. Follows the exact same shape as {@code
 * TicketService}: OWN/TEAM/DEPARTMENT/ORGANIZATION record-level authorization via {@link
 * ScopeAuthorizationService}, {@link #resolveLearner} defaulting a null {@code userId} to the
 * caller (self-enrollment) the same way {@code TicketService#resolveOwner} defaults a ticket's
 * owner - except the thing being defaulted is "who is this course for," not "who's responsible for
 * working this record."
 */
@Service
@RequiredArgsConstructor
public class CourseEnrollmentService {

    private static final Permission.Resource RESOURCE = Permission.Resource.COURSE_ENROLLMENT;

    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final CourseService courseService;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<CourseEnrollment> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleUserIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleUserIds
                .map(userIds -> courseEnrollmentRepository.findByOrganizationIdAndUserIdInAndDeletedAtIsNullOrderByCreatedAtDesc(
                        principal.getOrganizationId(), userIds, pageable))
                .orElseGet(() -> courseEnrollmentRepository.findByOrganizationIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                        principal.getOrganizationId(), pageable));
    }

    @Transactional(readOnly = true)
    public CourseEnrollment get(UserPrincipal principal, UUID enrollmentId) {
        CourseEnrollment enrollment = findOrThrow(principal.getOrganizationId(), enrollmentId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, enrollment.getUserId());
        return enrollment;
    }

    @Transactional
    public CourseEnrollment create(UserPrincipal principal, CreateCourseEnrollmentRequest request) {
        Course course = courseService.findOrThrow(principal.getOrganizationId(), request.courseId());
        UUID learnerId = resolveLearner(principal, request.userId());

        if (courseEnrollmentRepository.existsByOrganizationIdAndCourseIdAndUserIdAndDeletedAtIsNull(
                principal.getOrganizationId(), course.getId(), learnerId)) {
            throw new DuplicateResourceException("This learner is already enrolled in this course");
        }

        CourseEnrollment enrollment = new CourseEnrollment(principal.getOrganizationId(), course.getId(), learnerId);
        enrollment.setDueDate(request.dueDate());
        if (!learnerId.equals(principal.getId())) {
            enrollment.setAssignedByUserId(principal.getId());
        }
        courseEnrollmentRepository.save(enrollment);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "CourseEnrollment", enrollment.getId()));
        return enrollment;
    }

    /**
     * Advances an enrollment's status/score. Moving into {@code IN_PROGRESS} stamps {@link
     * CourseEnrollment#getStartedAt()} the first time only (re-submitting doesn't reset it). Moving
     * into {@code COMPLETED} or {@code FAILED} stamps {@link CourseEnrollment#getCompletedAt()} and
     * requires a {@code scorePercent}; the caller-requested {@code COMPLETED} is downgraded to
     * {@code FAILED} if the submitted score is below the course's {@link
     * Course#getPassingScorePercent()} - the same "trust the caller's status but verify it against
     * the real threshold" reasoning {@code CommissionEngine} applies by re-reading the Opportunity's
     * actual stage rather than trusting the event alone.
     */
    @Transactional
    public CourseEnrollment updateProgress(UserPrincipal principal, UUID enrollmentId, CourseEnrollment.Status requestedStatus, Integer scorePercent) {
        CourseEnrollment enrollment = findOrThrow(principal.getOrganizationId(), enrollmentId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, enrollment.getUserId());

        CourseEnrollment.Status finalStatus = requestedStatus;
        if (requestedStatus == CourseEnrollment.Status.IN_PROGRESS && enrollment.getStartedAt() == null) {
            enrollment.setStartedAt(Instant.now());
        }
        if (requestedStatus == CourseEnrollment.Status.COMPLETED || requestedStatus == CourseEnrollment.Status.FAILED) {
            Course course = courseService.findOrThrow(principal.getOrganizationId(), enrollment.getCourseId());
            boolean passed = scorePercent != null && scorePercent >= course.getPassingScorePercent();
            finalStatus = passed ? CourseEnrollment.Status.COMPLETED : CourseEnrollment.Status.FAILED;
            enrollment.setCompletedAt(Instant.now());
        }

        enrollment.setStatus(finalStatus);
        enrollment.setScorePercent(scorePercent);
        courseEnrollmentRepository.save(enrollment);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "CourseEnrollment", enrollment.getId()));
        return enrollment;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID enrollmentId) {
        CourseEnrollment enrollment = findOrThrow(principal.getOrganizationId(), enrollmentId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, enrollment.getUserId());

        enrollment.setDeletedAt(Instant.now());
        courseEnrollmentRepository.save(enrollment);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "CourseEnrollment", enrollmentId));
    }

    private CourseEnrollment findOrThrow(UUID organizationId, UUID enrollmentId) {
        return courseEnrollmentRepository.findActiveByIdAndOrganizationId(enrollmentId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("CourseEnrollment", enrollmentId));
    }

    private UUID resolveLearner(UserPrincipal principal, UUID requestedUserId) {
        if (requestedUserId == null || requestedUserId.equals(principal.getId())) {
            return principal.getId();
        }
        if (scopeAuthorizationService.highestGranted(principal, RESOURCE, Permission.Action.CREATE) != ScopeAuthorizationService.Access.ORGANIZATION) {
            throw new ForbiddenException("You can only enroll yourself in a course");
        }
        assertUserInOrganization(principal.getOrganizationId(), requestedUserId);
        return requestedUserId;
    }

    private void assertUserInOrganization(UUID organizationId, UUID userId) {
        boolean exists = userRepository.findActiveById(userId).map(u -> organizationId.equals(u.getOrganizationId())).orElse(false);
        if (!exists) {
            throw new ResourceNotFoundException("User", userId);
        }
    }
}
