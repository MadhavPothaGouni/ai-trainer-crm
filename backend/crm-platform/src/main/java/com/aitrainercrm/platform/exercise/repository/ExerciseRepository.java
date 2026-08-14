package com.aitrainercrm.platform.exercise.repository;

import com.aitrainercrm.platform.exercise.entity.Exercise;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExerciseRepository extends JpaRepository<Exercise, UUID> {

    @Query("select e from Exercise e where e.id = :id and e.organizationId = :organizationId and e.deletedAt is null")
    Optional<Exercise> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<Exercise> findByOrganizationIdAndDeletedAtIsNullOrderByNameAsc(UUID organizationId, Pageable pageable);

    /** Unpaginated active catalog - same reasoning CourseRepository#findByOrganizationIdAndActiveTrueAndDeletedAtIsNullOrderByTitleAsc already gives: a session-logging form needs the full picker list at once, not a page at a time. */
    List<Exercise> findByOrganizationIdAndActiveTrueAndDeletedAtIsNullOrderByNameAsc(UUID organizationId);

    boolean existsByOrganizationIdAndNameIgnoreCaseAndDeletedAtIsNull(UUID organizationId, String name);

    boolean existsByOrganizationIdAndNameIgnoreCaseAndIdNotAndDeletedAtIsNull(UUID organizationId, String name, UUID id);
}
