package com.aitrainercrm.platform.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.common.exception.DuplicateResourceException;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.course.dto.CreateCourseEnrollmentRequest;
import com.aitrainercrm.platform.course.entity.Course;
import com.aitrainercrm.platform.course.entity.CourseEnrollment;
import com.aitrainercrm.platform.course.repository.CourseEnrollmentRepository;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * See {@link CourseEnrollmentService}'s javadoc for the shape this mirrors ({@code TicketService}).
 * {@link CourseService} is mocked wholesale rather than its repository, since {@link
 * CourseEnrollmentService} only ever calls {@code CourseService#findOrThrow} through the real
 * (package-visible) method - constructing a real {@code CourseService} with a mocked repository
 * would be equivalent but noisier for no extra coverage.
 */
@ExtendWith(MockitoExtension.class)
class CourseEnrollmentServiceTest {

    @Mock private CourseEnrollmentRepository courseEnrollmentRepository;
    @Mock private CourseService courseService;
    @Mock private UserRepository userRepository;
    @Mock private ScopeAuthorizationService scopeAuthorizationService;
    @Mock private ApplicationEventPublisher events;

    private CourseEnrollmentService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CourseEnrollmentService(courseEnrollmentRepository, courseService, userRepository, scopeAuthorizationService, events);
    }

    private UserPrincipal principal(UUID userId) {
        return new UserPrincipal(userId, "learner@example.com", organizationId, List.of());
    }

    @Test
    void create_noUserIdRequested_selfEnrollsTheCaller() {
        UUID courseId = UUID.randomUUID();
        Course course = course(courseId, 70);
        when(courseService.findOrThrow(organizationId, courseId)).thenReturn(course);
        when(courseEnrollmentRepository.existsByOrganizationIdAndCourseIdAndUserIdAndDeletedAtIsNull(organizationId, courseId, callerId))
                .thenReturn(false);

        CourseEnrollment result = service.create(principal(callerId), new CreateCourseEnrollmentRequest(courseId, null, null));

        assertThat(result.getUserId()).isEqualTo(callerId);
        assertThat(result.getAssignedByUserId()).isNull();
        verify(courseEnrollmentRepository).save(result);
    }

    @Test
    void create_assigningSomeoneElseWithoutOrganizationScope_isForbidden() {
        UUID courseId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        when(courseService.findOrThrow(organizationId, courseId)).thenReturn(course(courseId, 70));
        when(scopeAuthorizationService.highestGranted(any(), any(), any())).thenReturn(ScopeAuthorizationService.Access.TEAM);

        assertThatThrownBy(() -> service.create(principal(callerId), new CreateCourseEnrollmentRequest(courseId, otherUserId, null)))
                .isInstanceOf(ForbiddenException.class);
        verify(courseEnrollmentRepository, never()).save(any());
    }

    @Test
    void create_duplicateActiveEnrollment_isRejected() {
        UUID courseId = UUID.randomUUID();
        when(courseService.findOrThrow(organizationId, courseId)).thenReturn(course(courseId, 70));
        when(courseEnrollmentRepository.existsByOrganizationIdAndCourseIdAndUserIdAndDeletedAtIsNull(organizationId, courseId, callerId))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(principal(callerId), new CreateCourseEnrollmentRequest(courseId, null, null)))
                .isInstanceOf(DuplicateResourceException.class);
        verify(courseEnrollmentRepository, never()).save(any());
    }

    @Test
    void updateProgress_scoreAtOrAbovePassingThreshold_landsInCompleted() {
        UUID enrollmentId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        CourseEnrollment enrollment = enrollment(enrollmentId, courseId, callerId);
        when(courseEnrollmentRepository.findActiveByIdAndOrganizationId(enrollmentId, organizationId)).thenReturn(Optional.of(enrollment));
        when(courseService.findOrThrow(organizationId, courseId)).thenReturn(course(courseId, 70));

        CourseEnrollment result = service.updateProgress(principal(callerId), enrollmentId, CourseEnrollment.Status.COMPLETED, 85);

        assertThat(result.getStatus()).isEqualTo(CourseEnrollment.Status.COMPLETED);
        assertThat(result.getCompletedAt()).isNotNull();
    }

    @Test
    void updateProgress_scoreBelowPassingThreshold_isDowngradedToFailed() {
        UUID enrollmentId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        CourseEnrollment enrollment = enrollment(enrollmentId, courseId, callerId);
        when(courseEnrollmentRepository.findActiveByIdAndOrganizationId(enrollmentId, organizationId)).thenReturn(Optional.of(enrollment));
        when(courseService.findOrThrow(organizationId, courseId)).thenReturn(course(courseId, 70));

        // Caller requests COMPLETED, but a 40% score is below the course's 70% passing bar.
        CourseEnrollment result = service.updateProgress(principal(callerId), enrollmentId, CourseEnrollment.Status.COMPLETED, 40);

        assertThat(result.getStatus()).isEqualTo(CourseEnrollment.Status.FAILED);
    }

    @Test
    void updateProgress_movingToInProgress_stampsStartedAtOnlyOnce() {
        UUID enrollmentId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        CourseEnrollment enrollment = enrollment(enrollmentId, courseId, callerId);
        java.time.Instant firstStart = java.time.Instant.now().minusSeconds(60);
        enrollment.setStartedAt(firstStart);
        when(courseEnrollmentRepository.findActiveByIdAndOrganizationId(enrollmentId, organizationId)).thenReturn(Optional.of(enrollment));

        CourseEnrollment result = service.updateProgress(principal(callerId), enrollmentId, CourseEnrollment.Status.IN_PROGRESS, null);

        assertThat(result.getStartedAt()).isEqualTo(firstStart);
    }

    private Course course(UUID id, int passingScorePercent) {
        Course course = new Course(organizationId, "Objection Handling 101", Course.Category.SALES);
        course.setId(id);
        course.setPassingScorePercent(passingScorePercent);
        return course;
    }

    private CourseEnrollment enrollment(UUID id, UUID courseId, UUID userId) {
        CourseEnrollment enrollment = new CourseEnrollment(organizationId, courseId, userId);
        enrollment.setId(id);
        return enrollment;
    }
}
