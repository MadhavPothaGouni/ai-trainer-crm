package com.aitrainercrm.platform.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.exercise.dto.CreatePersonalRecordRequest;
import com.aitrainercrm.platform.exercise.dto.UpdatePersonalRecordRequest;
import com.aitrainercrm.platform.exercise.entity.PersonalRecord;
import com.aitrainercrm.platform.exercise.repository.PersonalRecordRepository;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** See {@link PersonalRecordService}'s javadoc for the "must beat the current best" rule this mostly exists to cover. */
@ExtendWith(MockitoExtension.class)
class PersonalRecordServiceTest {

    @Mock private PersonalRecordRepository personalRecordRepository;
    @Mock private ExerciseService exerciseService;
    @Mock private ContactRepository contactRepository;
    @Mock private UserRepository userRepository;
    @Mock private ScopeAuthorizationService scopeAuthorizationService;
    @Mock private ApplicationEventPublisher events;

    private PersonalRecordService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID contactId = UUID.randomUUID();
    private final UUID exerciseId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PersonalRecordService(
                personalRecordRepository, exerciseService, contactRepository, userRepository, scopeAuthorizationService, events);
    }

    private UserPrincipal principal() {
        return new UserPrincipal(callerId, "coach@example.com", organizationId, List.of());
    }

    @Test
    void create_noPriorRecord_succeeds() {
        when(contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(contactId, organizationId)).thenReturn(true);
        when(personalRecordRepository.findBestValue(contactId, exerciseId, PersonalRecord.RecordType.ONE_REP_MAX))
                .thenReturn(Optional.empty());

        PersonalRecord record = service.create(
                principal(),
                new CreatePersonalRecordRequest(
                        contactId, exerciseId, PersonalRecord.RecordType.ONE_REP_MAX, new BigDecimal("225.0"), null, null, null));

        assertThat(record.getValue()).isEqualByComparingTo("225.0");
        assertThat(record.getOwnerId()).isEqualTo(callerId);
    }

    @Test
    void create_valueBeatsCurrentBest_succeeds() {
        when(contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(contactId, organizationId)).thenReturn(true);
        when(personalRecordRepository.findBestValue(contactId, exerciseId, PersonalRecord.RecordType.ONE_REP_MAX))
                .thenReturn(Optional.of(new BigDecimal("200.0")));

        PersonalRecord record = service.create(
                principal(),
                new CreatePersonalRecordRequest(
                        contactId, exerciseId, PersonalRecord.RecordType.ONE_REP_MAX, new BigDecimal("225.0"), null, null, null));

        assertThat(record.getValue()).isEqualByComparingTo("225.0");
    }

    @Test
    void create_valueDoesNotBeatCurrentBest_throwsNotAnImprovement() {
        when(contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(contactId, organizationId)).thenReturn(true);
        when(personalRecordRepository.findBestValue(contactId, exerciseId, PersonalRecord.RecordType.ONE_REP_MAX))
                .thenReturn(Optional.of(new BigDecimal("225.0")));

        assertThatThrownBy(() -> service.create(
                        principal(),
                        new CreatePersonalRecordRequest(
                                contactId, exerciseId, PersonalRecord.RecordType.ONE_REP_MAX, new BigDecimal("220.0"), null, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("PERSONAL_RECORD_NOT_AN_IMPROVEMENT"));
    }

    @Test
    void update_valueDoesNotBeatOtherRecords_throwsNotAnImprovement() {
        UUID recordId = UUID.randomUUID();
        PersonalRecord record =
                new PersonalRecord(organizationId, contactId, exerciseId, callerId, PersonalRecord.RecordType.MAX_WEIGHT, new BigDecimal("100.0"));
        record.setId(recordId);
        when(personalRecordRepository.findActiveByIdAndOrganizationId(recordId, organizationId)).thenReturn(Optional.of(record));
        when(personalRecordRepository.findBestValueExcluding(contactId, exerciseId, PersonalRecord.RecordType.MAX_WEIGHT, recordId))
                .thenReturn(Optional.of(new BigDecimal("150.0")));

        assertThatThrownBy(() -> service.update(principal(), recordId, new UpdatePersonalRecordRequest(new BigDecimal("140.0"), null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("PERSONAL_RECORD_NOT_AN_IMPROVEMENT"));
    }
}
