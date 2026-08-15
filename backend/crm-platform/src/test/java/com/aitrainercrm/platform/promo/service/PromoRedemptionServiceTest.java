package com.aitrainercrm.platform.promo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.promo.dto.CreatePromoRedemptionRequest;
import com.aitrainercrm.platform.promo.entity.PromoCode;
import com.aitrainercrm.platform.promo.repository.PromoRedemptionRepository;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** See {@link PromoRedemptionService}'s javadoc for the redeemability checks this mostly exists to cover. */
@ExtendWith(MockitoExtension.class)
class PromoRedemptionServiceTest {

    @Mock private PromoRedemptionRepository promoRedemptionRepository;
    @Mock private PromoCodeService promoCodeService;
    @Mock private ContactRepository contactRepository;
    @Mock private UserRepository userRepository;
    @Mock private ScopeAuthorizationService scopeAuthorizationService;
    @Mock private ApplicationEventPublisher events;

    private PromoRedemptionService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID promoCodeId = UUID.randomUUID();
    private final UUID contactId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PromoRedemptionService(
                promoRedemptionRepository, promoCodeService, contactRepository, userRepository, scopeAuthorizationService, events);
    }

    private UserPrincipal principal() {
        return new UserPrincipal(callerId, "staff@example.com", organizationId, List.of());
    }

    private PromoCode activePromoCode() {
        PromoCode promoCode = new PromoCode(organizationId, "SUMMER10", new BigDecimal("10.00"));
        promoCode.setId(promoCodeId);
        return promoCode;
    }

    @Test
    void create_inactivePromoCode_throwsBusinessException() {
        PromoCode promoCode = activePromoCode();
        promoCode.setActive(false);
        when(promoCodeService.findOrThrow(organizationId, promoCodeId)).thenReturn(promoCode);
        CreatePromoRedemptionRequest request = new CreatePromoRedemptionRequest(promoCodeId, contactId, null, null, null, null);

        assertThatThrownBy(() -> service.create(principal(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void create_expiredPromoCode_throwsBusinessException() {
        PromoCode promoCode = activePromoCode();
        promoCode.setExpiresAt(LocalDate.now().minusDays(1));
        when(promoCodeService.findOrThrow(organizationId, promoCodeId)).thenReturn(promoCode);
        CreatePromoRedemptionRequest request = new CreatePromoRedemptionRequest(promoCodeId, contactId, null, null, null, null);

        assertThatThrownBy(() -> service.create(principal(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void create_atRedemptionCap_throwsBusinessException() {
        PromoCode promoCode = activePromoCode();
        promoCode.setMaxRedemptions(2);
        when(promoCodeService.findOrThrow(organizationId, promoCodeId)).thenReturn(promoCode);
        when(promoRedemptionRepository.countByPromoCodeIdAndDeletedAtIsNull(promoCodeId)).thenReturn(2L);
        CreatePromoRedemptionRequest request = new CreatePromoRedemptionRequest(promoCodeId, contactId, null, null, null, null);

        assertThatThrownBy(() -> service.create(principal(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("redemption limit");
    }

    @Test
    void create_withinCapAndActive_succeeds() {
        PromoCode promoCode = activePromoCode();
        promoCode.setMaxRedemptions(2);
        when(promoCodeService.findOrThrow(organizationId, promoCodeId)).thenReturn(promoCode);
        when(promoRedemptionRepository.countByPromoCodeIdAndDeletedAtIsNull(promoCodeId)).thenReturn(1L);
        when(contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(contactId, organizationId)).thenReturn(true);
        CreatePromoRedemptionRequest request = new CreatePromoRedemptionRequest(promoCodeId, contactId, null, new BigDecimal("5.00"), null, null);

        var result = service.create(principal(), request);

        assertThat(result.getOwnerId()).isEqualTo(callerId);
        assertThat(result.getAmountDiscounted()).isEqualByComparingTo("5.00");
    }
}
