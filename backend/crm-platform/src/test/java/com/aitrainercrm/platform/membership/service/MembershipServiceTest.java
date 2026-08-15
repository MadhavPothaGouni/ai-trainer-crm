package com.aitrainercrm.platform.membership.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.membership.dto.CreateMembershipRequest;
import com.aitrainercrm.platform.membership.entity.Membership;
import com.aitrainercrm.platform.membership.entity.MembershipPlan;
import com.aitrainercrm.platform.membership.repository.MembershipRepository;
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

/** See {@link MembershipService}'s javadoc for the shape this mirrors ({@code ClientGoalService}). */
@ExtendWith(MockitoExtension.class)
class MembershipServiceTest {

    @Mock private MembershipRepository membershipRepository;
    @Mock private MembershipPlanService membershipPlanService;
    @Mock private ContactRepository contactRepository;
    @Mock private UserRepository userRepository;
    @Mock private ScopeAuthorizationService scopeAuthorizationService;
    @Mock private ApplicationEventPublisher events;

    private MembershipService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID contactId = UUID.randomUUID();
    private final UUID planId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new MembershipService(
                membershipRepository, membershipPlanService, contactRepository, userRepository, scopeAuthorizationService, events);
    }

    private UserPrincipal principal(UUID userId) {
        return new UserPrincipal(userId, "coach@example.com", organizationId, List.of());
    }

    private MembershipPlan plan(BigDecimal price, Integer sessionCredits) {
        MembershipPlan plan = new MembershipPlan(organizationId, "Unlimited Monthly");
        plan.setId(planId);
        plan.setPrice(price);
        plan.setSessionCredits(sessionCredits);
        return plan;
    }

    private CreateMembershipRequest createRequest(UUID ownerId) {
        return new CreateMembershipRequest(contactId, planId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), true, "Signed up in person", ownerId);
    }

    @Test
    void create_snapshotsThePlansCurrentPriceAndSessionCredits() {
        when(contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(contactId, organizationId)).thenReturn(true);
        when(membershipPlanService.findOrThrow(organizationId, planId)).thenReturn(plan(new BigDecimal("149.00"), 10));

        Membership result = service.create(principal(callerId), createRequest(null));

        assertThat(result.getOwnerId()).isEqualTo(callerId);
        assertThat(result.getBillingCyclePrice()).isEqualByComparingTo("149.00");
        assertThat(result.getRemainingCredits()).isEqualTo(10);
        assertThat(result.getStatus()).isEqualTo(Membership.Status.ACTIVE);
        verify(membershipRepository).save(result);
    }

    @Test
    void create_assigningSomeoneElseWithoutOrganizationScope_isForbidden() {
        UUID otherUserId = UUID.randomUUID();
        when(scopeAuthorizationService.highestGranted(any(), any(), any())).thenReturn(ScopeAuthorizationService.Access.TEAM);

        assertThatThrownBy(() -> service.create(principal(callerId), createRequest(otherUserId))).isInstanceOf(ForbiddenException.class);
        verify(membershipRepository, never()).save(any());
    }

    @Test
    void updateStatus_movingToPaused_stampsPausedAt() {
        UUID membershipId = UUID.randomUUID();
        Membership membership = new Membership(organizationId, contactId, planId, callerId, LocalDate.of(2026, 1, 1));
        membership.setId(membershipId);
        when(membershipRepository.findActiveByIdAndOrganizationId(membershipId, organizationId)).thenReturn(Optional.of(membership));

        Membership result = service.updateStatus(principal(callerId), membershipId, Membership.Status.PAUSED);

        assertThat(result.getStatus()).isEqualTo(Membership.Status.PAUSED);
        assertThat(result.getPausedAt()).isNotNull();
    }

    @Test
    void updateStatus_movingToCancelled_stampsCancelledAtAndClearsNextBillingDate() {
        UUID membershipId = UUID.randomUUID();
        Membership membership = new Membership(organizationId, contactId, planId, callerId, LocalDate.of(2026, 1, 1));
        membership.setId(membershipId);
        membership.setNextBillingDate(LocalDate.of(2026, 2, 1));
        when(membershipRepository.findActiveByIdAndOrganizationId(membershipId, organizationId)).thenReturn(Optional.of(membership));

        Membership result = service.updateStatus(principal(callerId), membershipId, Membership.Status.CANCELLED);

        assertThat(result.getStatus()).isEqualTo(Membership.Status.CANCELLED);
        assertThat(result.getCancelledAt()).isNotNull();
        assertThat(result.getNextBillingDate()).isNull();
    }
}
