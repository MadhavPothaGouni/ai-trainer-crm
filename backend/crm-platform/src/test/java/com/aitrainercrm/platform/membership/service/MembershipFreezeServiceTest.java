package com.aitrainercrm.platform.membership.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.membership.dto.CreateMembershipFreezeRequest;
import com.aitrainercrm.platform.membership.dto.UpdateMembershipFreezeRequest;
import com.aitrainercrm.platform.membership.entity.MembershipFreeze;
import com.aitrainercrm.platform.membership.repository.MembershipFreezeRepository;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
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

/** See {@link MembershipFreezeService}'s javadoc for the overlap-conflict rule this mostly exists to cover. */
@ExtendWith(MockitoExtension.class)
class MembershipFreezeServiceTest {

    @Mock private MembershipFreezeRepository membershipFreezeRepository;
    @Mock private MembershipService membershipService;
    @Mock private UserRepository userRepository;
    @Mock private ScopeAuthorizationService scopeAuthorizationService;
    @Mock private ApplicationEventPublisher events;

    private MembershipFreezeService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID membershipId = UUID.randomUUID();

    private final LocalDate freezeStart = LocalDate.now().plusDays(1);
    private final LocalDate freezeEnd = freezeStart.plusWeeks(1);

    @BeforeEach
    void setUp() {
        service = new MembershipFreezeService(membershipFreezeRepository, membershipService, userRepository, scopeAuthorizationService, events);
    }

    private UserPrincipal principal() {
        return new UserPrincipal(callerId, "staff@example.com", organizationId, List.of());
    }

    @Test
    void create_noOverlap_succeeds() {
        when(membershipFreezeRepository.existsByMembershipIdAndStatusInAndDeletedAtIsNullAndFreezeStartLessThanAndFreezeEndGreaterThan(
                        eq(membershipId), any(), any(), any()))
                .thenReturn(false);

        MembershipFreeze freeze = service.create(
                principal(), new CreateMembershipFreezeRequest(membershipId, freezeStart, freezeEnd, "Medical leave", null, null));

        assertThat(freeze.getStatus()).isEqualTo(MembershipFreeze.Status.REQUESTED);
        assertThat(freeze.getOwnerId()).isEqualTo(callerId);
    }

    @Test
    void create_overlappingActiveFreezeExists_throwsConflict() {
        when(membershipFreezeRepository.existsByMembershipIdAndStatusInAndDeletedAtIsNullAndFreezeStartLessThanAndFreezeEndGreaterThan(
                        eq(membershipId), any(), any(), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(
                        principal(), new CreateMembershipFreezeRequest(membershipId, freezeStart, freezeEnd, "Medical leave", null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("MEMBERSHIP_FREEZE_CONFLICT"));
    }

    @Test
    void create_endNotAfterStart_throwsInvalidRange() {
        assertThatThrownBy(() -> service.create(
                        principal(), new CreateMembershipFreezeRequest(membershipId, freezeEnd, freezeStart, "Medical leave", null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("MEMBERSHIP_FREEZE_INVALID_RANGE"));
    }

    @Test
    void update_movingRangeIntoAnotherFreezesWindow_throwsConflict() {
        UUID freezeId = UUID.randomUUID();
        MembershipFreeze freeze = new MembershipFreeze(organizationId, membershipId, callerId, freezeStart, freezeEnd);
        freeze.setId(freezeId);
        when(membershipFreezeRepository.findActiveByIdAndOrganizationId(freezeId, organizationId)).thenReturn(Optional.of(freeze));
        when(membershipFreezeRepository
                        .existsByMembershipIdAndStatusInAndDeletedAtIsNullAndIdNotAndFreezeStartLessThanAndFreezeEndGreaterThan(
                                eq(membershipId), any(), eq(freezeId), any(), any()))
                .thenReturn(true);

        LocalDate newStart = freezeStart.plusDays(2);
        LocalDate newEnd = newStart.plusWeeks(1);

        assertThatThrownBy(() -> service.update(principal(), freezeId, new UpdateMembershipFreezeRequest(newStart, newEnd, "Extended", null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("MEMBERSHIP_FREEZE_CONFLICT"));
    }

    @Test
    void updateStatus_reactivatingAnEndedFreeze_reChecksOverlap() {
        UUID freezeId = UUID.randomUUID();
        MembershipFreeze freeze = new MembershipFreeze(organizationId, membershipId, callerId, freezeStart, freezeEnd);
        freeze.setId(freezeId);
        freeze.setStatus(MembershipFreeze.Status.ENDED);
        when(membershipFreezeRepository.findActiveByIdAndOrganizationId(freezeId, organizationId)).thenReturn(Optional.of(freeze));
        when(membershipFreezeRepository
                        .existsByMembershipIdAndStatusInAndDeletedAtIsNullAndIdNotAndFreezeStartLessThanAndFreezeEndGreaterThan(
                                eq(membershipId), any(), eq(freezeId), any(), any()))
                .thenReturn(false);

        MembershipFreeze reactivated = service.updateStatus(principal(), freezeId, MembershipFreeze.Status.ACTIVE);

        assertThat(reactivated.getStatus()).isEqualTo(MembershipFreeze.Status.ACTIVE);
    }
}
