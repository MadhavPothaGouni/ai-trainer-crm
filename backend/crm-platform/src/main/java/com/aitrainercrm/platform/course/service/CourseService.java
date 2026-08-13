package com.aitrainercrm.platform.course.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.course.dto.CreateCourseRequest;
import com.aitrainercrm.platform.course.dto.UpdateCourseRequest;
import com.aitrainercrm.platform.course.entity.Course;
import com.aitrainercrm.platform.course.repository.CourseRepository;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The training catalog. No {@link com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService}
 * calls here - see {@link Course}'s javadoc and {@code ProductService}'s identical reasoning: Course
 * has no {@code ownerId}, so the controller's {@code @PreAuthorize} (any of TEAM/DEPARTMENT/
 * ORGANIZATION) is the whole authorization story for this service.
 */
@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<Course> list(UserPrincipal principal, Pageable pageable) {
        return courseRepository.findByOrganizationIdAndDeletedAtIsNullOrderByTitleAsc(principal.getOrganizationId(), pageable);
    }

    /** Unpaginated - {@code CourseEnrollmentController}'s create form and {@code CourseEnrollmentDto} enrichment on the frontend both need the full active catalog at once, the same reasoning {@code RegionController#list} gives for its own unpaginated tree. */
    @Transactional(readOnly = true)
    public List<Course> listActive(UserPrincipal principal) {
        return courseRepository.findByOrganizationIdAndActiveTrueAndDeletedAtIsNullOrderByTitleAsc(principal.getOrganizationId());
    }

    @Transactional(readOnly = true)
    public Course get(UserPrincipal principal, UUID courseId) {
        return findOrThrow(principal.getOrganizationId(), courseId);
    }

    @Transactional
    public Course create(UserPrincipal principal, CreateCourseRequest request) {
        Course course = new Course(principal.getOrganizationId(), request.title(), request.category());
        applyFields(course, request.description(), request.durationMinutes(), request.passingScorePercent());
        courseRepository.save(course);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "Course", course.getId()));
        return course;
    }

    @Transactional
    public Course update(UserPrincipal principal, UUID courseId, UpdateCourseRequest request) {
        Course course = findOrThrow(principal.getOrganizationId(), courseId);
        course.setTitle(request.title());
        course.setCategory(request.category());
        course.setActive(request.active());
        applyFields(course, request.description(), request.durationMinutes(), request.passingScorePercent());
        courseRepository.save(course);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Course", course.getId()));
        return course;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID courseId) {
        Course course = findOrThrow(principal.getOrganizationId(), courseId);
        course.setDeletedAt(Instant.now());
        courseRepository.save(course);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "Course", courseId));
    }

    Course findOrThrow(UUID organizationId, UUID courseId) {
        return courseRepository.findActiveByIdAndOrganizationId(courseId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
    }

    private void applyFields(Course course, String description, int durationMinutes, int passingScorePercent) {
        course.setDescription(description);
        course.setDurationMinutes(durationMinutes);
        course.setPassingScorePercent(passingScorePercent);
    }
}
