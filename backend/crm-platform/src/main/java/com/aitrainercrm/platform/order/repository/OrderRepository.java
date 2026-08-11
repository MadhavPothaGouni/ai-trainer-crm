package com.aitrainercrm.platform.order.repository;

import com.aitrainercrm.platform.order.entity.Order;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    @Query("select o from Order o where o.id = :id and o.organizationId = :organizationId and o.deletedAt is null")
    Optional<Order> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<Order> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    /** Existence + tenant check used by InvoiceService when generating an invoice from an order. */
    boolean existsByIdAndOrganizationIdAndDeletedAtIsNull(UUID id, UUID organizationId);
}
