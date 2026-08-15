package com.aitrainercrm.platform.compensation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.compensation.dto.CreateCompensationRecordRequest;
import com.aitrainercrm.platform.compensation.entity.CompensationRecord;
import com.aitrainercrm.platform.compensation.repository.CompensationRecordRepository;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.entity.User;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** See {@link CompensationRecordService}'s javadoc for the totalAmount-computation behavior this mostly exists to cover. */
@ExtendWith(MockitoExtension.class)
class CompensationRecordServiceTest {

    @Mock private CompensationRecordRepository compensationRecordRepository;
    @Mock private UserRepository userRepository;
    @Mock private ScopeAuthorizationService scopeAuthorizationService;
    @Mock private ApplicationEventPublisher events;

    private CompensationRecordService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID staffUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CompensationRecordService(compensationRecordRepository, userRepository, scopeAuthorizationService, events);
    }

    private UserPrincipal principal() {
        return new UserPrincipal(callerId, "manager@example.com", organizationId, List.of());
    }

    private User activeStaffUser() {
        User user = new User();
        user.setId(staffUserId);
        user.setOrganizationId(organizationId);
        return user;
    }

    @Test
    void create_computesTotalAmountFromWagesCommissionAndBonus() {
        when(userRepository.findActiveById(staffUserId)).thenReturn(Optional.of(activeStaffUser()));

        CompensationRecord record = service.create(
                principal(),
                new CreateCompensationRecordRequest(
                        staffUserId,
                        LocalDate.of(2026, 8, 1),
                        LocalDate.of(2026, 8, 15),
                        new BigDecimal("40.00"),
                        new BigDecimal("25.00"),
                        new BigDecimal("100.00"),
                        new BigDecimal("50.00"),
                        null,
                        null));

        // 40 * 25 + 100 + 50 = 1150
        assertThat(record.getTotalAmount()).isEqualByComparingTo("1150.00");
        assertThat(record.getOwnerId()).isEqualTo(callerId);
    }

    @Test
    void create_nullCommissionAndBonus_treatedAsZero() {
        when(userRepository.findActiveById(staffUserId)).thenReturn(Optional.of(activeStaffUser()));

        CompensationRecord record = service.create(
                principal(),
                new CreateCompensationRecordRequest(
                        staffUserId, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 15), new BigDecimal("10.00"), new BigDecimal("20.00"), null, null, null, null));

        assertThat(record.getTotalAmount()).isEqualByComparingTo("200.00");
    }

    @Test
    void create_payPeriodEndBeforeStart_throwsInvalidPeriod() {
        when(userRepository.findActiveById(staffUserId)).thenReturn(Optional.of(activeStaffUser()));

        assertThatThrownBy(() -> service.create(
                        principal(),
                        new CreateCompensationRecordRequest(
                                staffUserId,
                                LocalDate.of(2026, 8, 15),
                                LocalDate.of(2026, 8, 1),
                                BigDecimal.TEN,
                                BigDecimal.TEN,
                                null,
                                null,
                                null,
                                null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("COMPENSATION_RECORD_INVALID_PERIOD"));
    }

    @Test
    void updateStatus_movingToPaidThenBackAndForth_stampsPaidAtOnlyOnce() {
        UUID recordId = UUID.randomUUID();
        CompensationRecord record = new CompensationRecord(
                organizationId, staffUserId, callerId, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 15));
        record.setId(recordId);
        when(compensationRecordRepository.findActiveByIdAndOrganizationId(recordId, organizationId)).thenReturn(Optional.of(record));

        CompensationRecord paid = service.updateStatus(principal(), recordId, CompensationRecord.Status.PAID);
        assertThat(paid.getPaidAt()).isNotNull();
        var paidAt = paid.getPaidAt();

        CompensationRecord backToDraft = service.updateStatus(principal(), recordId, CompensationRecord.Status.DRAFT);
        assertThat(backToDraft.getPaidAt()).isEqualTo(paidAt);

        CompensationRecord paidAgain = service.updateStatus(principal(), recordId, CompensationRecord.Status.PAID);
        assertThat(paidAgain.getPaidAt()).isEqualTo(paidAt);
    }
}
