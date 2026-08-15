package com.aitrainercrm.platform.checkin.repository;

import com.aitrainercrm.platform.checkin.entity.ClientCheckIn;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientCheckInRepository extends JpaRepository<ClientCheckIn, UUID> {

    @Query("select c from ClientCheckIn c where c.id = :id and c.organizationId = :organizationId and c.deletedAt is null")
    Optional<ClientCheckIn> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<ClientCheckIn> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    Page<ClientCheckIn> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(UUID organizationId, Set<UUID> ownerIds, Pageable pageable);
}
