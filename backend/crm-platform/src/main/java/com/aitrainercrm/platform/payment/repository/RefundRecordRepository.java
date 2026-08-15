package com.aitrainercrm.platform.payment.repository;

import com.aitrainercrm.platform.payment.entity.RefundRecord;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefundRecordRepository extends JpaRepository<RefundRecord, UUID> {

    @Query("select r from RefundRecord r where r.id = :id and r.organizationId = :organizationId and r.deletedAt is null")
    Optional<RefundRecord> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<RefundRecord> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    Page<RefundRecord> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(UUID organizationId, Set<UUID> ownerIds, Pageable pageable);

    /** Used by {@code RefundRecordService#assertRefundNotExceedingPayment} when creating a new refund (nothing to exclude yet). */
    @Query("select coalesce(sum(r.amount), 0) from RefundRecord r where r.paymentId = :paymentId and r.deletedAt is null")
    BigDecimal sumActiveAmountByPaymentId(@Param("paymentId") UUID paymentId);

    /** Same sum as above, but excluding the refund being updated. */
    @Query("select coalesce(sum(r.amount), 0) from RefundRecord r where r.paymentId = :paymentId and r.id <> :excludeId and r.deletedAt is null")
    BigDecimal sumActiveAmountByPaymentIdExcluding(@Param("paymentId") UUID paymentId, @Param("excludeId") UUID excludeId);
}
