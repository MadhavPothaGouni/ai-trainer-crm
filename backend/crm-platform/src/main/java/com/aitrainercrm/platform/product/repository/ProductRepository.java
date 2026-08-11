package com.aitrainercrm.platform.product.repository;

import com.aitrainercrm.platform.product.entity.Product;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    @Query("select p from Product p where p.id = :id and p.organizationId = :organizationId and p.deletedAt is null")
    Optional<Product> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<Product> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    /** Existence + tenant check used by QuoteService when a line item references a product. */
    boolean existsByIdAndOrganizationIdAndDeletedAtIsNull(UUID id, UUID organizationId);
}
