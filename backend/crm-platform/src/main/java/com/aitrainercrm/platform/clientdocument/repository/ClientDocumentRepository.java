package com.aitrainercrm.platform.clientdocument.repository;

import com.aitrainercrm.platform.clientdocument.entity.ClientDocument;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientDocumentRepository extends JpaRepository<ClientDocument, UUID> {

    @Query("select d from ClientDocument d where d.id = :id and d.organizationId = :organizationId and d.deletedAt is null")
    Optional<ClientDocument> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<ClientDocument> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    Page<ClientDocument> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(UUID organizationId, Set<UUID> ownerIds, Pageable pageable);
}
