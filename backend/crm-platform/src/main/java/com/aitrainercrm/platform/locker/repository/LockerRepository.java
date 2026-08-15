package com.aitrainercrm.platform.locker.repository;

import com.aitrainercrm.platform.locker.entity.Locker;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LockerRepository extends JpaRepository<Locker, UUID> {

    @Query("select l from Locker l where l.id = :id and l.organizationId = :organizationId and l.deletedAt is null")
    Optional<Locker> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<Locker> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    boolean existsByIdAndOrganizationIdAndDeletedAtIsNull(UUID id, UUID organizationId);
}
