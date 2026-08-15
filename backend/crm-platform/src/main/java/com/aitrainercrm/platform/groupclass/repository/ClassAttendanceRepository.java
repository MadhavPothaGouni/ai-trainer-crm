package com.aitrainercrm.platform.groupclass.repository;

import com.aitrainercrm.platform.groupclass.entity.ClassAttendance;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClassAttendanceRepository extends JpaRepository<ClassAttendance, UUID> {

    @Query("select a from ClassAttendance a where a.id = :id and a.organizationId = :organizationId and a.deletedAt is null")
    Optional<ClassAttendance> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<ClassAttendance> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    Page<ClassAttendance> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(UUID organizationId, Set<UUID> ownerIds, Pageable pageable);

    List<ClassAttendance> findByClassSessionIdAndDeletedAtIsNull(UUID classSessionId);

    /** Used by ClassAttendanceService to enforce a session's capacity - counts everyone currently holding the roster (REGISTERED or ATTENDED), excluding CANCELLED/NO_SHOW. */
    long countByClassSessionIdAndDeletedAtIsNullAndStatusIn(UUID classSessionId, List<ClassAttendance.Status> statuses);
}
