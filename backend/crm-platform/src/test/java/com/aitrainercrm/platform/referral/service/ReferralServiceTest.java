package com.aitrainercrm.platform.referral.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.referral.dto.CreateReferralRequest;
import com.aitrainercrm.platform.referral.entity.Referral;
import com.aitrainercrm.platform.referral.repository.ReferralRepository;
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

/** See {@link ReferralService}'s javadoc for the shape this mirrors ({@code ClientGoalService}). */
@ExtendWith(MockitoExtension.class)
class ReferralServiceTest {

    @Mock private ReferralRepository referralRepository;
    @Mock private ContactRepository contactRepository;
    @Mock private UserRepository userRepository;
    @Mock private ScopeAuthorizationService scopeAuthorizationService;
    @Mock private ApplicationEventPublisher events;

    private ReferralService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID referrerContactId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ReferralService(referralRepository, contactRepository, userRepository, scopeAuthorizationService, events);
    }

    private UserPrincipal principal(UUID userId) {
        return new UserPrincipal(userId, "coach@example.com", organizationId, List.of());
    }

    @Test
    void create_noOwnerIdRequested_selfAssignsTheCaller() {
        when(contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(referrerContactId, organizationId)).thenReturn(true);
        CreateReferralRequest request = new CreateReferralRequest(referrerContactId, "Alex Friend", null, null, new BigDecimal("25.00"), null, null);

        Referral result = service.create(principal(callerId), request);

        assertThat(result.getOwnerId()).isEqualTo(callerId);
        assertThat(result.getReferrerContactId()).isEqualTo(referrerContactId);
        assertThat(result.getStatus()).isEqualTo(Referral.Status.PENDING);
        verify(referralRepository).save(result);
    }

    @Test
    void create_assigningSomeoneElseWithoutOrganizationScope_isForbidden() {
        UUID otherUserId = UUID.randomUUID();
        when(scopeAuthorizationService.highestGranted(any(), any(), any())).thenReturn(ScopeAuthorizationService.Access.TEAM);
        CreateReferralRequest request = new CreateReferralRequest(referrerContactId, "Alex Friend", null, null, null, null, otherUserId);

        assertThatThrownBy(() -> service.create(principal(callerId), request)).isInstanceOf(ForbiddenException.class);
        verify(referralRepository, never()).save(any());
    }

    @Test
    void updateStatus_movingToConvertedWithAContact_stampsConvertedContactIdOnlyOnce() {
        UUID referralId = UUID.randomUUID();
        UUID firstContactId = UUID.randomUUID();
        UUID secondContactId = UUID.randomUUID();
        Referral referral = new Referral(organizationId, referrerContactId, "Alex Friend", callerId);
        referral.setId(referralId);
        when(referralRepository.findActiveByIdAndOrganizationId(referralId, organizationId)).thenReturn(Optional.of(referral));
        when(contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(firstContactId, organizationId)).thenReturn(true);

        Referral firstConversion = service.updateStatus(principal(callerId), referralId, Referral.Status.CONVERTED, firstContactId);
        assertThat(firstConversion.getConvertedContactId()).isEqualTo(firstContactId);

        // Re-entering CONVERTED with a different contact must not move the original.
        Referral secondConversion = service.updateStatus(principal(callerId), referralId, Referral.Status.CONVERTED, secondContactId);
        assertThat(secondConversion.getConvertedContactId()).isEqualTo(firstContactId);
    }

    @Test
    void issueReward_withNoRewardAmountSet_throwsBusinessException() {
        UUID referralId = UUID.randomUUID();
        Referral referral = new Referral(organizationId, referrerContactId, "Alex Friend", callerId);
        referral.setId(referralId);
        when(referralRepository.findActiveByIdAndOrganizationId(referralId, organizationId)).thenReturn(Optional.of(referral));

        assertThatThrownBy(() -> service.issueReward(principal(callerId), referralId)).isInstanceOf(BusinessException.class);
        assertThat(referral.getRewardIssuedAt()).isNull();
    }

    @Test
    void issueReward_calledTwice_stampsRewardIssuedAtOnlyOnce() {
        UUID referralId = UUID.randomUUID();
        Referral referral = new Referral(organizationId, referrerContactId, "Alex Friend", callerId);
        referral.setId(referralId);
        referral.setRewardAmount(new BigDecimal("25.00"));
        when(referralRepository.findActiveByIdAndOrganizationId(referralId, organizationId)).thenReturn(Optional.of(referral));

        Referral firstIssue = service.issueReward(principal(callerId), referralId);
        Instant firstIssuedAt = firstIssue.getRewardIssuedAt();
        assertThat(firstIssuedAt).isNotNull();

        Referral secondIssue = service.issueReward(principal(callerId), referralId);
        assertThat(secondIssue.getRewardIssuedAt()).isEqualTo(firstIssuedAt);
    }
}
