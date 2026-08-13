package com.aitrainercrm.platform.sequence.repository;

import com.aitrainercrm.platform.sequence.entity.SequenceEnrollment;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SequenceEnrollmentRepository extends JpaRepository<SequenceEnrollment, UUID> {

    @Query("select e from SequenceEnrollment e where e.id = :id and e.organizationId = :organizationId and e.deletedAt is null")
    Optional<SequenceEnrollment> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<SequenceEnrollment> findByOrganizationIdAndDeletedAtIsNullOrderByEnrolledAtDesc(UUID organizationId, Pageable pageable);

    Page<SequenceEnrollment> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNullOrderByEnrolledAtDesc(
            UUID organizationId, Set<UUID> ownerIds, Pageable pageable);

    boolean existsByOrganizationIdAndSequenceIdAndTargetTypeAndTargetIdAndDeletedAtIsNull(
            UUID organizationId, UUID sequenceId, SequenceEnrollment.TargetType targetType, UUID targetId);
}
