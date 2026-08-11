package com.aitrainercrm.platform.invoice.repository;

import com.aitrainercrm.platform.invoice.entity.Invoice;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    @Query("select i from Invoice i where i.id = :id and i.organizationId = :organizationId and i.deletedAt is null")
    Optional<Invoice> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<Invoice> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    Page<Invoice> findByOrganizationIdAndOrderIdAndDeletedAtIsNull(UUID organizationId, UUID orderId, Pageable pageable);
}
