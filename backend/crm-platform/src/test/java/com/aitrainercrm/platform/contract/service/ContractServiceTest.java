package com.aitrainercrm.platform.contract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.account.repository.AccountRepository;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.DuplicateResourceException;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.contract.dto.CreateContractRequest;
import com.aitrainercrm.platform.contract.dto.UpdateContractRequest;
import com.aitrainercrm.platform.contract.entity.Contract;
import com.aitrainercrm.platform.contract.repository.ContractRepository;
import com.aitrainercrm.platform.opportunity.repository.OpportunityRepository;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
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

/** See {@link ContractService}'s javadoc for the shape this mirrors ({@code TicketService}). */
@ExtendWith(MockitoExtension.class)
class ContractServiceTest {

    @Mock private ContractRepository contractRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private OpportunityRepository opportunityRepository;
    @Mock private UserRepository userRepository;
    @Mock private ScopeAuthorizationService scopeAuthorizationService;
    @Mock private ApplicationEventPublisher events;

    private ContractService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ContractService(contractRepository, accountRepository, opportunityRepository, userRepository, scopeAuthorizationService, events);
    }

    private UserPrincipal principal(UUID userId) {
        return new UserPrincipal(userId, "rep@example.com", organizationId, List.of());
    }

    private CreateContractRequest createRequest(String contractNumber, UUID ownerId) {
        return new CreateContractRequest(
                accountId, null, contractNumber, "Annual Support Agreement", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                new BigDecimal("12000.00"), false, null, "Standard terms", ownerId);
    }

    @Test
    void create_noOwnerIdRequested_selfAssignsTheCaller() {
        when(accountRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(accountId, organizationId)).thenReturn(true);
        when(contractRepository.existsByOrganizationIdAndContractNumberAndDeletedAtIsNull(organizationId, "C-1001")).thenReturn(false);

        Contract result = service.create(principal(callerId), createRequest("C-1001", null));

        assertThat(result.getOwnerId()).isEqualTo(callerId);
        assertThat(result.getContractNumber()).isEqualTo("C-1001");
        assertThat(result.getStatus()).isEqualTo(Contract.Status.DRAFT);
        verify(contractRepository).save(result);
    }

    @Test
    void create_assigningSomeoneElseWithoutOrganizationScope_isForbidden() {
        UUID otherUserId = UUID.randomUUID();
        when(scopeAuthorizationService.highestGranted(any(), any(), any())).thenReturn(ScopeAuthorizationService.Access.TEAM);

        assertThatThrownBy(() -> service.create(principal(callerId), createRequest("C-1001", otherUserId)))
                .isInstanceOf(ForbiddenException.class);
        verify(contractRepository, never()).save(any());
    }

    @Test
    void create_duplicateContractNumber_isRejected() {
        when(accountRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(accountId, organizationId)).thenReturn(true);
        when(contractRepository.existsByOrganizationIdAndContractNumberAndDeletedAtIsNull(organizationId, "C-1001")).thenReturn(true);

        assertThatThrownBy(() -> service.create(principal(callerId), createRequest("C-1001", null)))
                .isInstanceOf(DuplicateResourceException.class);
        verify(contractRepository, never()).save(any());
    }

    @Test
    void create_endDateBeforeStartDate_isRejected() {
        when(accountRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(accountId, organizationId)).thenReturn(true);
        CreateContractRequest request = new CreateContractRequest(
                accountId, null, "C-1001", "Bad Dates", LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1),
                BigDecimal.TEN, false, null, null, null);

        assertThatThrownBy(() -> service.create(principal(callerId), request)).isInstanceOf(BusinessException.class);
        verify(contractRepository, never()).save(any());
    }

    @Test
    void create_autoRenewWithoutRenewalTerm_isRejected() {
        when(accountRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(accountId, organizationId)).thenReturn(true);
        CreateContractRequest request = new CreateContractRequest(
                accountId, null, "C-1001", "Auto Renew", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                BigDecimal.TEN, true, null, null, null);

        assertThatThrownBy(() -> service.create(principal(callerId), request)).isInstanceOf(BusinessException.class);
        verify(contractRepository, never()).save(any());
    }

    @Test
    void updateStatus_movingToActiveForTheFirstTime_stampsSignedAt() {
        UUID contractId = UUID.randomUUID();
        Contract contract = new Contract(organizationId, accountId, callerId, "C-1001", "Agreement", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        contract.setId(contractId);
        when(contractRepository.findActiveByIdAndOrganizationId(contractId, organizationId)).thenReturn(Optional.of(contract));

        Contract result = service.updateStatus(principal(callerId), contractId, Contract.Status.ACTIVE);

        assertThat(result.getStatus()).isEqualTo(Contract.Status.ACTIVE);
        assertThat(result.getSignedAt()).isNotNull();
    }

    @Test
    void updateStatus_movingToActiveASecondTime_doesNotOverwriteSignedAt() {
        UUID contractId = UUID.randomUUID();
        Contract contract = new Contract(organizationId, accountId, callerId, "C-1001", "Agreement", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        contract.setId(contractId);
        java.time.Instant originalSignedAt = java.time.Instant.parse("2026-01-05T00:00:00Z");
        contract.setSignedAt(originalSignedAt);
        contract.setStatus(Contract.Status.TERMINATED);
        when(contractRepository.findActiveByIdAndOrganizationId(contractId, organizationId)).thenReturn(Optional.of(contract));

        Contract result = service.updateStatus(principal(callerId), contractId, Contract.Status.ACTIVE);

        assertThat(result.getSignedAt()).isEqualTo(originalSignedAt);
    }

    @Test
    void update_contractKeepingItsOwnNumber_isNotTreatedAsAConflict() {
        UUID contractId = UUID.randomUUID();
        Contract contract = new Contract(organizationId, accountId, callerId, "C-1001", "Agreement", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        contract.setId(contractId);
        when(contractRepository.findActiveByIdAndOrganizationId(contractId, organizationId)).thenReturn(Optional.of(contract));
        when(contractRepository.existsByOrganizationIdAndContractNumberAndDeletedAtIsNull(organizationId, "C-1001")).thenReturn(true);

        UpdateContractRequest request = new UpdateContractRequest(
                null, "C-1001", "Renamed Agreement", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), BigDecimal.TEN, false, null, null);

        Contract result = service.update(principal(callerId), contractId, request);

        assertThat(result.getTitle()).isEqualTo("Renamed Agreement");
    }
}
