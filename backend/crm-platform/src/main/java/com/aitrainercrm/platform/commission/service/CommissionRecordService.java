package com.aitrainercrm.platform.commission.service;

import com.aitrainercrm.platform.commission.dto.UpdateCommissionRecordStatusRequest;
import com.aitrainercrm.platform.commission.entity.CommissionRecord;
import com.aitrainercrm.platform.commission.repository.CommissionRecordRepository;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read access plus the one mutation an API can ever make to a {@link CommissionRecord}: walking
 * its {@link CommissionRecord.Status} forward. Records themselves are never created or edited here
 * - {@code CommissionEngine} is the only writer of everything except {@code status}/{@code paidAt}
 * - so unlike every other CRUD-shaped service in this codebase, there is no {@code create}/{@code
 * update} here, only {@link #list}, {@link #get}, {@link #myRecords}, and {@link #updateStatus}.
 *
 * <p>{@link #myRecords} needs no permission at all - the fourth-kind, notification-style
 * self-scoped shape {@code SalesGoalService#myGoals} and {@code SavedViewService} already
 * established, reused here rather than invented fresh.
 */
@Service
@RequiredArgsConstructor
public class CommissionRecordService {

    private final CommissionRecordRepository commissionRecordRepository;

    @Transactional(readOnly = true)
    public Page<CommissionRecord> list(UUID organizationId, Pageable pageable) {
        return commissionRecordRepository.findByOrganizationIdOrderByEarnedAtDesc(organizationId, pageable);
    }

    @Transactional(readOnly = true)
    public CommissionRecord get(UUID organizationId, UUID recordId) {
        return findOrThrow(organizationId, recordId);
    }

    @Transactional(readOnly = true)
    public List<CommissionRecord> myRecords(UserPrincipal principal) {
        return commissionRecordRepository.findByOrganizationIdAndOwnerUserIdOrderByEarnedAtDesc(
                principal.getOrganizationId(), principal.getId());
    }

    /** Only PENDING -> APPROVED and APPROVED -> PAID are legal - see {@code
     * UpdateCommissionRecordStatusRequest}'s javadoc. Setting {@code paidAt} is folded into this
     * same transition rather than a separate endpoint, since it only ever means one thing: the
     * moment this record reached PAID. */
    @Transactional
    public CommissionRecord updateStatus(UUID organizationId, UUID recordId, UpdateCommissionRecordStatusRequest request) {
        CommissionRecord record = findOrThrow(organizationId, recordId);
        assertLegalTransition(record.getStatus(), request.status());

        record.setStatus(request.status());
        if (request.status() == CommissionRecord.Status.PAID) {
            record.setPaidAt(Instant.now());
        }
        commissionRecordRepository.save(record);
        return record;
    }

    private void assertLegalTransition(CommissionRecord.Status from, CommissionRecord.Status to) {
        boolean legal = (from == CommissionRecord.Status.PENDING && to == CommissionRecord.Status.APPROVED)
                || (from == CommissionRecord.Status.APPROVED && to == CommissionRecord.Status.PAID);
        if (!legal) {
            throw new BusinessException(
                    "COMMISSION_RECORD_TRANSITION", "Cannot move a commission record from %s to %s.".formatted(from, to),
                    HttpStatus.BAD_REQUEST);
        }
    }

    private CommissionRecord findOrThrow(UUID organizationId, UUID recordId) {
        return commissionRecordRepository.findByIdAndOrganizationId(recordId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("CommissionRecord", recordId));
    }
}
