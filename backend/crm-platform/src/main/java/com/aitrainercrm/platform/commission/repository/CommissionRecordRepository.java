package com.aitrainercrm.platform.commission.repository;

import com.aitrainercrm.platform.commission.entity.CommissionRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommissionRecordRepository extends JpaRepository<CommissionRecord, UUID> {

    Optional<CommissionRecord> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Page<CommissionRecord> findByOrganizationIdOrderByEarnedAtDesc(UUID organizationId, Pageable pageable);

    /** CommissionRecordService#myRecords - the self-scoped shape notification/'s inbox and salesgoals/'s /mine endpoint already use. */
    List<CommissionRecord> findByOrganizationIdAndOwnerUserIdOrderByEarnedAtDesc(UUID organizationId, UUID ownerUserId);

    /** CommissionEngine's idempotency guard - see its javadoc for why this check plus the real
     * uq_commission_records_opportunity unique constraint (V29) together make double-crediting a
     * deal impossible even under concurrent listener invocations. */
    boolean existsByOpportunityId(UUID opportunityId);
}
