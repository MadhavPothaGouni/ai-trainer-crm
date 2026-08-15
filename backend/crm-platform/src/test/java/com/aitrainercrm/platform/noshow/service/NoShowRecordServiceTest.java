package com.aitrainercrm.platform.noshow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.noshow.entity.NoShowRecord;
import com.aitrainercrm.platform.noshow.repository.NoShowRecordRepository;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** See {@link NoShowRecordService}'s javadoc for the fee-waiving behavior {@link #waive} mostly exists to cover. */
@ExtendWith(MockitoExtension.class)
class NoShowRecordServiceTest {

    @Mock private NoShowRecordRepository noShowRecordRepository;
    @Mock private ContactRepository contactRepository;
    @Mock private UserRepository userRepository;
    @Mock private ScopeAuthorizationService scopeAuthorizationService;
    @Mock private ApplicationEventPublisher events;

    private NoShowRecordService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID contactId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new NoShowRecordService(noShowRecordRepository, contactRepository, userRepository, scopeAuthorizationService, events);
    }

    private UserPrincipal principal() {
        return new UserPrincipal(callerId, "staff@example.com", organizationId, List.of());
    }

    private NoShowRecord recordWithFee(BigDecimal feeAmount) {
        NoShowRecord record = new NoShowRecord(organizationId, contactId, callerId, Instant.now());
        record.setId(UUID.randomUUID());
        record.setFeeAmount(feeAmount);
        return record;
    }

    @Test
    void waive_feeSetAndNotYetWaived_flipsWaivedAndStampsWaivedAt() {
        NoShowRecord record = recordWithFee(new BigDecimal("25.00"));
        when(noShowRecordRepository.findActiveByIdAndOrganizationId(record.getId(), organizationId)).thenReturn(Optional.of(record));

        NoShowRecord waived = service.waive(principal(), record.getId());

        assertThat(waived.isWaived()).isTrue();
        assertThat(waived.getWaivedAt()).isNotNull();
    }

    @Test
    void waive_noFeeSet_throwsNoFee() {
        NoShowRecord record = recordWithFee(null);
        when(noShowRecordRepository.findActiveByIdAndOrganizationId(record.getId(), organizationId)).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.waive(principal(), record.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("NO_SHOW_RECORD_NO_FEE"));
    }

    @Test
    void waive_alreadyWaived_throwsAlreadyWaived() {
        NoShowRecord record = recordWithFee(new BigDecimal("25.00"));
        record.setWaived(true);
        record.setWaivedAt(Instant.now());
        when(noShowRecordRepository.findActiveByIdAndOrganizationId(record.getId(), organizationId)).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.waive(principal(), record.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("NO_SHOW_RECORD_ALREADY_WAIVED"));
    }
}
