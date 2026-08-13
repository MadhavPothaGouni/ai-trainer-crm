package com.aitrainercrm.platform.clientgoal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.clientgoal.dto.CreateClientGoalRequest;
import com.aitrainercrm.platform.clientgoal.entity.ClientGoal;
import com.aitrainercrm.platform.clientgoal.repository.ClientGoalRepository;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
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

/** See {@link ClientGoalService}'s javadoc for the shape this mirrors ({@code ContractService}). */
@ExtendWith(MockitoExtension.class)
class ClientGoalServiceTest {

    @Mock private ClientGoalRepository clientGoalRepository;
    @Mock private ContactRepository contactRepository;
    @Mock private UserRepository userRepository;
    @Mock private ScopeAuthorizationService scopeAuthorizationService;
    @Mock private ApplicationEventPublisher events;

    private ClientGoalService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID contactId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ClientGoalService(clientGoalRepository, contactRepository, userRepository, scopeAuthorizationService, events);
    }

    private UserPrincipal principal(UUID userId) {
        return new UserPrincipal(userId, "coach@example.com", organizationId, List.of());
    }

    private CreateClientGoalRequest createRequest(UUID ownerId) {
        return new CreateClientGoalRequest(
                contactId, "Lose 15 lbs", ClientGoal.GoalType.WEIGHT_LOSS, "lbs",
                new BigDecimal("200"), new BigDecimal("185"), new BigDecimal("200"), LocalDate.of(2027, 1, 1), "Kickoff session done", ownerId);
    }

    @Test
    void create_noOwnerIdRequested_selfAssignsTheCaller() {
        when(contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(contactId, organizationId)).thenReturn(true);

        ClientGoal result = service.create(principal(callerId), createRequest(null));

        assertThat(result.getOwnerId()).isEqualTo(callerId);
        assertThat(result.getContactId()).isEqualTo(contactId);
        assertThat(result.getStatus()).isEqualTo(ClientGoal.Status.ACTIVE);
        verify(clientGoalRepository).save(result);
    }

    @Test
    void create_assigningSomeoneElseWithoutOrganizationScope_isForbidden() {
        UUID otherUserId = UUID.randomUUID();
        when(scopeAuthorizationService.highestGranted(any(), any(), any())).thenReturn(ScopeAuthorizationService.Access.TEAM);

        assertThatThrownBy(() -> service.create(principal(callerId), createRequest(otherUserId))).isInstanceOf(ForbiddenException.class);
        verify(clientGoalRepository, never()).save(any());
    }

    @Test
    void updateStatus_movingToAchievedForTheFirstTime_stampsAchievedAt() {
        UUID goalId = UUID.randomUUID();
        ClientGoal goal = new ClientGoal(organizationId, contactId, callerId, "Lose 15 lbs");
        goal.setId(goalId);
        when(clientGoalRepository.findActiveByIdAndOrganizationId(goalId, organizationId)).thenReturn(Optional.of(goal));

        ClientGoal result = service.updateStatus(principal(callerId), goalId, ClientGoal.Status.ACHIEVED);

        assertThat(result.getStatus()).isEqualTo(ClientGoal.Status.ACHIEVED);
        assertThat(result.getAchievedAt()).isNotNull();
    }

    @Test
    void updateStatus_movingToAchievedASecondTime_doesNotOverwriteAchievedAt() {
        UUID goalId = UUID.randomUUID();
        ClientGoal goal = new ClientGoal(organizationId, contactId, callerId, "Lose 15 lbs");
        goal.setId(goalId);
        Instant originalAchievedAt = Instant.parse("2026-06-01T00:00:00Z");
        goal.setAchievedAt(originalAchievedAt);
        goal.setStatus(ClientGoal.Status.ON_HOLD);
        when(clientGoalRepository.findActiveByIdAndOrganizationId(goalId, organizationId)).thenReturn(Optional.of(goal));

        ClientGoal result = service.updateStatus(principal(callerId), goalId, ClientGoal.Status.ACHIEVED);

        assertThat(result.getAchievedAt()).isEqualTo(originalAchievedAt);
    }
}
