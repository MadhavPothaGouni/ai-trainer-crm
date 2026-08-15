package com.aitrainercrm.platform.giftcard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.giftcard.entity.GiftCard;
import com.aitrainercrm.platform.giftcard.repository.GiftCardRepository;
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

/** See {@link GiftCardService}'s javadoc for the balance-deduction behavior {@link #redeem} mostly exists to cover. */
@ExtendWith(MockitoExtension.class)
class GiftCardServiceTest {

    @Mock private GiftCardRepository giftCardRepository;
    @Mock private ContactRepository contactRepository;
    @Mock private UserRepository userRepository;
    @Mock private ScopeAuthorizationService scopeAuthorizationService;
    @Mock private ApplicationEventPublisher events;

    private GiftCardService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID contactId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new GiftCardService(giftCardRepository, contactRepository, userRepository, scopeAuthorizationService, events);
    }

    private UserPrincipal principal() {
        return new UserPrincipal(callerId, "staff@example.com", organizationId, List.of());
    }

    private GiftCard giftCardWithBalance(BigDecimal balance) {
        GiftCard giftCard = new GiftCard(organizationId, contactId, callerId, "GC-100", new BigDecimal("100.00"));
        giftCard.setId(UUID.randomUUID());
        giftCard.setCurrentBalance(balance);
        return giftCard;
    }

    @Test
    void redeem_partialAmount_deductsBalanceAndLeavesCardActive() {
        GiftCard giftCard = giftCardWithBalance(new BigDecimal("100.00"));
        when(giftCardRepository.findActiveByIdAndOrganizationId(giftCard.getId(), organizationId)).thenReturn(Optional.of(giftCard));

        GiftCard redeemed = service.redeem(principal(), giftCard.getId(), new BigDecimal("40.00"));

        assertThat(redeemed.getCurrentBalance()).isEqualByComparingTo("60.00");
        assertThat(redeemed.getStatus()).isEqualTo(GiftCard.Status.ACTIVE);
        assertThat(redeemed.getRedeemedAt()).isNull();
    }

    @Test
    void redeem_fullRemainingBalance_movesToRedeemedAndStampsRedeemedAtOnce() {
        GiftCard giftCard = giftCardWithBalance(new BigDecimal("40.00"));
        when(giftCardRepository.findActiveByIdAndOrganizationId(giftCard.getId(), organizationId)).thenReturn(Optional.of(giftCard));

        GiftCard redeemed = service.redeem(principal(), giftCard.getId(), new BigDecimal("40.00"));

        assertThat(redeemed.getCurrentBalance()).isEqualByComparingTo("0.00");
        assertThat(redeemed.getStatus()).isEqualTo(GiftCard.Status.REDEEMED);
        assertThat(redeemed.getRedeemedAt()).isNotNull();
    }

    @Test
    void redeem_amountExceedsBalance_throwsInsufficientBalance() {
        GiftCard giftCard = giftCardWithBalance(new BigDecimal("10.00"));
        when(giftCardRepository.findActiveByIdAndOrganizationId(giftCard.getId(), organizationId)).thenReturn(Optional.of(giftCard));

        assertThatThrownBy(() -> service.redeem(principal(), giftCard.getId(), new BigDecimal("20.00")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("GIFT_CARD_INSUFFICIENT_BALANCE"));
    }

    @Test
    void redeem_cardNotActive_throwsNotActive() {
        GiftCard giftCard = giftCardWithBalance(new BigDecimal("10.00"));
        giftCard.setStatus(GiftCard.Status.CANCELLED);
        when(giftCardRepository.findActiveByIdAndOrganizationId(giftCard.getId(), organizationId)).thenReturn(Optional.of(giftCard));

        assertThatThrownBy(() -> service.redeem(principal(), giftCard.getId(), new BigDecimal("5.00")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("GIFT_CARD_NOT_ACTIVE"));
    }

    @Test
    void redeem_expiredCard_throwsExpired() {
        GiftCard giftCard = giftCardWithBalance(new BigDecimal("10.00"));
        giftCard.setExpiresAt(LocalDate.now().minusDays(1));
        when(giftCardRepository.findActiveByIdAndOrganizationId(giftCard.getId(), organizationId)).thenReturn(Optional.of(giftCard));

        assertThatThrownBy(() -> service.redeem(principal(), giftCard.getId(), new BigDecimal("5.00")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("GIFT_CARD_EXPIRED"));
    }
}
