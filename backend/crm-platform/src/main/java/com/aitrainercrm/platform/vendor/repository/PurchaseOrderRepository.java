package com.aitrainercrm.platform.vendor.repository;

import com.aitrainercrm.platform.vendor.entity.PurchaseOrder;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    @Query("select p from PurchaseOrder p where p.id = :id and p.organizationId = :organizationId and p.deletedAt is null")
    Optional<PurchaseOrder> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<PurchaseOrder> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    Page<PurchaseOrder> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(UUID organizationId, Set<UUID> ownerIds, Pageable pageable);
}
