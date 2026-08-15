package com.aitrainercrm.platform.progressphoto.repository;

import com.aitrainercrm.platform.progressphoto.entity.ProgressPhoto;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProgressPhotoRepository extends JpaRepository<ProgressPhoto, UUID> {

    @Query("select p from ProgressPhoto p where p.id = :id and p.organizationId = :organizationId and p.deletedAt is null")
    Optional<ProgressPhoto> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<ProgressPhoto> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    Page<ProgressPhoto> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(UUID organizationId, Set<UUID> ownerIds, Pageable pageable);
}
