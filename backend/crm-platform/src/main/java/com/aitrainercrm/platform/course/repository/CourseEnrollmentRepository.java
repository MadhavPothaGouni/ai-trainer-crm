package com.aitrainercrm.platform.course.repository;

import com.aitrainercrm.platform.course.entity.CourseEnrollment;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseEnrollmentRepository extends JpaRepository<CourseEnrollment, UUID> {

    @Query("select e from CourseEnrollment e where e.id = :id and e.organizationId = :organizationId and e.deletedAt is null")
    Optional<CourseEnrollment> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<CourseEnrollment> findByOrganizationIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    Page<CourseEnrollment> findByOrganizationIdAndUserIdInAndDeletedAtIsNullOrderByCreatedAtDesc(
            UUID organizationId, Set<UUID> userIds, Pageable pageable);

    List<CourseEnrollment> findByOrganizationIdAndCourseIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID organizationId, UUID courseId);

    /** The exact uniqueness check {@code uq_course_enrollments_course_user_active} (V31) enforces at the database level - checked in the service first, for a clean {@code DuplicateResourceException} instead of a raw constraint-violation 409. */
    boolean existsByOrganizationIdAndCourseIdAndUserIdAndDeletedAtIsNull(UUID organizationId, UUID courseId, UUID userId);

    long countByOrganizationIdAndCourseIdAndStatusAndDeletedAtIsNull(UUID organizationId, UUID courseId, CourseEnrollment.Status status);
}
