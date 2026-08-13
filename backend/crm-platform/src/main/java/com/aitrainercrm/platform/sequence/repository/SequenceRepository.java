package com.aitrainercrm.platform.sequence.repository;

import com.aitrainercrm.platform.sequence.entity.Sequence;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SequenceRepository extends JpaRepository<Sequence, UUID> {

    @Query("select s from Sequence s where s.id = :id and s.organizationId = :organizationId and s.deletedAt is null")
    Optional<Sequence> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<Sequence> findByOrganizationIdAndDeletedAtIsNullOrderByNameAsc(UUID organizationId, Pageable pageable);

    List<Sequence> findByOrganizationIdAndActiveTrueAndDeletedAtIsNullOrderByNameAsc(UUID organizationId);
}
