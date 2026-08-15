package com.aitrainercrm.platform.groupclass.repository;

import com.aitrainercrm.platform.groupclass.entity.ClassWaitlist;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClassWaitlistRepository extends JpaRepository<ClassWaitlist, UUID> {

    @Query("select w from ClassWaitlist w where w.id = :id and w.organizationId = :organizationId and w.deletedAt is null")
    Optional<ClassWaitlist> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<ClassWaitlist> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    Page<ClassWaitlist> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(UUID organizationId, Set<UUID> ownerIds, Pageable pageable);

    long countByClassSessionIdAndStatusAndDeletedAtIsNull(UUID classSessionId, ClassWaitlist.Status status);
}
