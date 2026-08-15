package com.aitrainercrm.platform.loyalty.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.loyalty.dto.CreateLoyaltyTransactionRequest;
import com.aitrainercrm.platform.loyalty.entity.LoyaltyTransaction;
import com.aitrainercrm.platform.loyalty.repository.LoyaltyTransactionRepository;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** See {@link LoyaltyTransactionService}'s javadoc for the sign-validation and balance-aggregation behavior covered here. */
@ExtendWith(MockitoExtension.class)
class LoyaltyTransactionServiceTest {

    @Mock private LoyaltyTransactionRepository loyaltyTransactionRepository;
    @Mock private ContactRepository contactRepository;
    @Mock private UserRepository userRepository;
    @Mock private ScopeAuthorizationService scopeAuthorizationService;
    @Mock private ApplicationEventPublisher events;

    private LoyaltyTransactionService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID contactId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new LoyaltyTransactionService(loyaltyTransactionRepository, contactRepository, userRepository, scopeAuthorizationService, events);
    }

    private UserPrincipal principal() {
        return new UserPrincipal(callerId, "staff@example.com", organizationId, List.of());
    }

    @Test
    void create_earnedCheckinWithPositivePoints_succeeds() {
        when(contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(contactId, organizationId)).thenReturn(true);

        LoyaltyTransaction transaction = service.create(
                principal(), new CreateLoyaltyTransactionRequest(contactId, 10, LoyaltyTransaction.Reason.EARNED_CHECKIN, null, null));

        assertThat(transaction.getPoints()).isEqualTo(10);
        assertThat(transaction.getReason()).isEqualTo(LoyaltyTransaction.Reason.EARNED_CHECKIN);
    }

    @Test
    void create_earnedCheckinWithNegativePoints_throwsInvalidSign() {
        when(contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(contactId, organizationId)).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                        principal(), new CreateLoyaltyTransactionRequest(contactId, -10, LoyaltyTransaction.Reason.EARNED_CHECKIN, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("LOYALTY_TRANSACTION_INVALID_SIGN"));
    }

    @Test
    void create_redeemedRewardWithPositivePoints_throwsInvalidSign() {
        when(contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(contactId, organizationId)).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                        principal(), new CreateLoyaltyTransactionRequest(contactId, 5, LoyaltyTransaction.Reason.REDEEMED_REWARD, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("LOYALTY_TRANSACTION_INVALID_SIGN"));
    }

    @Test
    void create_manualAdjustmentWithNegativePoints_succeeds() {
        when(contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(contactId, organizationId)).thenReturn(true);

        LoyaltyTransaction transaction = service.create(
                principal(), new CreateLoyaltyTransactionRequest(contactId, -3, LoyaltyTransaction.Reason.MANUAL_ADJUSTMENT, "Correction", null));

        assertThat(transaction.getPoints()).isEqualTo(-3);
    }

    @Test
    void getBalance_organizationScopedCaller_sumsAcrossAllOwners() {
        when(contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(contactId, organizationId)).thenReturn(true);
        when(scopeAuthorizationService.visibleOwnerIds(any(UserPrincipal.class), eq(Permission.Resource.LOYALTY_TRANSACTION), eq(Permission.Action.READ)))
                .thenReturn(Optional.empty());
        when(loyaltyTransactionRepository.sumPointsByOrganizationIdAndContactId(organizationId, contactId)).thenReturn(42L);

        long balance = service.getBalance(principal(), contactId);

        assertThat(balance).isEqualTo(42L);
    }

    @Test
    void getBalance_ownScopedCaller_sumsOnlyVisibleOwners() {
        when(contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(contactId, organizationId)).thenReturn(true);
        Set<UUID> visibleOwnerIds = Set.of(callerId);
        when(scopeAuthorizationService.visibleOwnerIds(any(UserPrincipal.class), eq(Permission.Resource.LOYALTY_TRANSACTION), eq(Permission.Action.READ)))
                .thenReturn(Optional.of(visibleOwnerIds));
        when(loyaltyTransactionRepository.sumPointsByOrganizationIdAndContactIdAndOwnerIdIn(organizationId, contactId, visibleOwnerIds)).thenReturn(7L);

        long balance = service.getBalance(principal(), contactId);

        assertThat(balance).isEqualTo(7L);
    }
}
