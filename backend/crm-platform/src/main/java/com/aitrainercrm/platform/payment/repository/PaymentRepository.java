package com.aitrainercrm.platform.payment.repository;

import com.aitrainercrm.platform.payment.entity.Payment;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    @Query("select p from Payment p where p.id = :id and p.organizationId = :organizationId and p.deletedAt is null")
    Optional<Payment> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<Payment> findByOrganizationIdAndInvoiceIdAndDeletedAtIsNullOrderByPaidAtDesc(UUID organizationId, UUID invoiceId, Pageable pageable);

    List<Payment> findByInvoiceIdAndDeletedAtIsNull(UUID invoiceId);

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.invoiceId = :invoiceId and p.deletedAt is null")
    BigDecimal sumActiveAmountByInvoiceId(@Param("invoiceId") UUID invoiceId);
}
