package com.aitrainercrm.platform.commission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.commission.dto.UpdateCommissionRecordStatusRequest;
import com.aitrainercrm.platform.commission.entity.CommissionPlan;
import com.aitrainercrm.platform.commission.entity.CommissionRecord;
import com.aitrainercrm.platform.commission.repository.CommissionRecordRepository;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommissionRecordServiceTest {

    @Mock private CommissionRecordRepository commissionRecordRepository;

    private CommissionRecordService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID recordId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CommissionRecordService(commissionRecordRepository);
    }

    @Test
    void get_unknownRecord_throwsResourceNotFound() {
        when(commissionRecordRepository.findByIdAndOrganizationId(recordId, organizationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(organizationId, recordId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void myRecords_delegatesToOwnerScopedQuery() {
        UserPrincipal principal = new UserPrincipal(ownerId, "rep@example.com", organizationId, List.of());
        CommissionRecord record = record(CommissionRecord.Status.PENDING);
        when(commissionRecordRepository.findByOrganizationIdAndOwnerUserIdOrderByEarnedAtDesc(organizationId, ownerId))
                .thenReturn(List.of(record));

        List<CommissionRecord> result = service.myRecords(principal);

        assertThat(result).containsExactly(record);
    }

    @Test
    void updateStatus_pendingToApproved_isLegal() {
        CommissionRecord record = record(CommissionRecord.Status.PENDING);
        when(commissionRecordRepository.findByIdAndOrganizationId(recordId, organizationId)).thenReturn(Optional.of(record));

        CommissionRecord result =
                service.updateStatus(organizationId, recordId, new UpdateCommissionRecordStatusRequest(CommissionRecord.Status.APPROVED));

        assertThat(result.getStatus()).isEqualTo(CommissionRecord.Status.APPROVED);
        assertThat(result.getPaidAt()).isNull();
        verify(commissionRecordRepository).save(record);
    }

    @Test
    void updateStatus_approvedToPaid_setsAndPaidAt() {
        CommissionRecord record = record(CommissionRecord.Status.APPROVED);
        when(commissionRecordRepository.findByIdAndOrganizationId(recordId, organizationId)).thenReturn(Optional.of(record));

        CommissionRecord result =
                service.updateStatus(organizationId, recordId, new UpdateCommissionRecordStatusRequest(CommissionRecord.Status.PAID));

        assertThat(result.getStatus()).isEqualTo(CommissionRecord.Status.PAID);
        assertThat(result.getPaidAt()).isNotNull();
    }

    @Test
    void updateStatus_pendingDirectlyToPaid_isIllegal() {
        CommissionRecord record = record(CommissionRecord.Status.PENDING);
        when(commissionRecordRepository.findByIdAndOrganizationId(recordId, organizationId)).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.updateStatus(
                        organizationId, recordId, new UpdateCommissionRecordStatusRequest(CommissionRecord.Status.PAID)))
                .isInstanceOf(BusinessException.class);
        verify(commissionRecordRepository, never()).save(any());
    }

    @Test
    void updateStatus_approvedBackToPending_isIllegal() {
        CommissionRecord record = record(CommissionRecord.Status.APPROVED);
        when(commissionRecordRepository.findByIdAndOrganizationId(recordId, organizationId)).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.updateStatus(
                        organizationId, recordId, new UpdateCommissionRecordStatusRequest(CommissionRecord.Status.PENDING)))
                .isInstanceOf(BusinessException.class);
        verify(commissionRecordRepository, never()).save(any());
    }

    @Test
    void updateStatus_paidIsTerminal_cannotMoveAnywhere() {
        CommissionRecord record = record(CommissionRecord.Status.PAID);
        when(commissionRecordRepository.findByIdAndOrganizationId(recordId, organizationId)).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.updateStatus(
                        organizationId, recordId, new UpdateCommissionRecordStatusRequest(CommissionRecord.Status.APPROVED)))
                .isInstanceOf(BusinessException.class);
    }

    private CommissionRecord record(CommissionRecord.Status status) {
        CommissionRecord record = new CommissionRecord(
                organizationId, UUID.randomUUID(), ownerId, UUID.randomUUID(), new BigDecimal("1000.00"), CommissionPlan.RateType.PERCENTAGE,
                new BigDecimal("5.00"), new BigDecimal("50.00"));
        record.setId(recordId);
        record.setStatus(status);
        return record;
    }
}
