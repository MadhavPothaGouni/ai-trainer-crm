package com.aitrainercrm.platform.course.repository;

import com.aitrainercrm.platform.course.entity.Course;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRepository extends JpaRepository<Course, UUID> {

    @Query("select c from Course c where c.id = :id and c.organizationId = :organizationId and c.deletedAt is null")
    Optional<Course> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    boolean existsByIdAndOrganizationIdAndDeletedAtIsNull(UUID id, UUID organizationId);

    Page<Course> findByOrganizationIdAndDeletedAtIsNullOrderByTitleAsc(UUID organizationId, Pageable pageable);

    /** {@code CourseEnrollmentService} resolves a course's category/passing score without pagination when listing enrollments alongside course details. */
    List<Course> findByOrganizationIdAndDeletedAtIsNullOrderByTitleAsc(UUID organizationId);

    List<Course> findByOrganizationIdAndActiveTrueAndDeletedAtIsNullOrderByTitleAsc(UUID organizationId);
}
