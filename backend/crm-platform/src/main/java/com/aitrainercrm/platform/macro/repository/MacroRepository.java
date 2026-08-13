package com.aitrainercrm.platform.macro.repository;

import com.aitrainercrm.platform.macro.entity.Macro;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MacroRepository extends JpaRepository<Macro, UUID> {

    @Query("select m from Macro m where m.id = :id and m.organizationId = :organizationId and m.deletedAt is null")
    Optional<Macro> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<Macro> findByOrganizationIdAndDeletedAtIsNullOrderByNameAsc(UUID organizationId, Pageable pageable);

    /** Unpaginated active catalog - mirrors CourseRepository/SequenceRepository's identical helper, used by the "apply a macro" picker on a ticket. */
    List<Macro> findByOrganizationIdAndActiveTrueAndDeletedAtIsNullOrderByNameAsc(UUID organizationId);
}
