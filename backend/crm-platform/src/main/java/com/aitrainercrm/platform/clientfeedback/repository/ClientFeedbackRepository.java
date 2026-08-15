package com.aitrainercrm.platform.clientfeedback.repository;

import com.aitrainercrm.platform.clientfeedback.entity.ClientFeedback;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientFeedbackRepository extends JpaRepository<ClientFeedback, UUID> {

    @Query("select f from ClientFeedback f where f.id = :id and f.organizationId = :organizationId and f.deletedAt is null")
    Optional<ClientFeedback> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<ClientFeedback> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    Page<ClientFeedback> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(UUID organizationId, Set<UUID> ownerIds, Pageable pageable);
}
