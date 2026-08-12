package com.aitrainercrm.platform.salesgoals.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.opportunity.entity.Opportunity;
import com.aitrainercrm.platform.organization.repository.TeamRepository;
import com.aitrainercrm.platform.salesgoals.dto.CreateSalesGoalRequest;
import com.aitrainercrm.platform.salesgoals.dto.SalesGoalDto;
import com.aitrainercrm.platform.salesgoals.dto.UpdateSalesGoalRequest;
import com.aitrainercrm.platform.salesgoals.dto.WonTotalsDto;
import com.aitrainercrm.platform.salesgoals.entity.SalesGoal;
import com.aitrainercrm.platform.salesgoals.repository.SalesGoalProgressRepository;
import com.aitrainercrm.platform.salesgoals.repository.SalesGoalRepository;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.entity.User;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
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

@ExtendWith(MockitoExtension.class)
class SalesGoalServiceTest {

    @Mock private SalesGoalRepository salesGoalRepository;
    @Mock private SalesGoalProgressRepository salesGoalProgressRepository;
    @Mock private UserRepository userRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private ApplicationEventPublisher events;
    @Mock private UserPrincipal principal;

    private SalesGoalService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID repId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SalesGoalService(salesGoalRepository, salesGoalProgressRepository, userRepository, teamRepository, events);
    }

    @Test
    void create_neitherTargetSet_returns400() {
        CreateSalesGoalRequest request = new CreateSalesGoalRequest(
                "Q3 quota", null, null, SalesGoal.Metric.REVENUE, new BigDecimal("10000"), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30));
        when(principal.getOrganizationId()).thenReturn(organizationId);

        assertThatThrownBy(() -> service.create(principal, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Exactly one");
        verify(salesGoalRepository, never()).save(any());
    }

    @Test
    void create_bothTargetsSet_returns400() {
        CreateSalesGoalRequest request = new CreateSalesGoalRequest(
                "Q3 quota", repId, UUID.randomUUID(), SalesGoal.Metric.REVENUE, new BigDecimal("10000"), LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 9, 30));
        when(principal.getOrganizationId()).thenReturn(organizationId);

        assertThatThrownBy(() -> service.create(principal, request)).isInstanceOf(BusinessException.class);
    }

    @Test
    void create_periodEndBeforePeriodStart_returns400() {
        CreateSalesGoalRequest request = new CreateSalesGoalRequest(
                "Bad period", repId, null, SalesGoal.Metric.REVENUE, new BigDecimal("10000"), LocalDate.of(2026, 9, 30),
                LocalDate.of(2026, 7, 1));
        when(principal.getOrganizationId()).thenReturn(organizationId);

        assertThatThrownBy(() -> service.create(principal, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("periodStart must not be after periodEnd");
    }

    @Test
    void create_individualGoal_computesLiveProgressFromWonOpportunities() {
        CreateSalesGoalRequest request = new CreateSalesGoalRequest(
                "Q3 quota", repId, null, SalesGoal.Metric.REVENUE, new BigDecimal("10000"), LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 9, 30));
        when(principal.getOrganizationId()).thenReturn(organizationId);
        when(principal.getId()).thenReturn(UUID.randomUUID());
        when(userRepository.findActiveById(repId)).thenReturn(Optional.of(activeUser(repId, organizationId)));
        when(salesGoalProgressRepository.sumWonBetween(
                        eq(organizationId), eq(Set.of(repId)), eq(Opportunity.Stage.CLOSED_WON), eq(LocalDate.of(2026, 7, 1)),
                        eq(LocalDate.of(2026, 9, 30))))
                .thenReturn(new WonTotalsDto(3, new BigDecimal("6000")));

        SalesGoalDto result = service.create(principal, request);

        assertThat(result.actualValue()).isEqualByComparingTo("6000");
        assertThat(result.percentComplete()).isEqualByComparingTo("60.0");
        verify(salesGoalRepository).save(any(SalesGoal.class));
    }

    @Test
    void create_dealCountMetric_usesCountNotSum() {
        CreateSalesGoalRequest request = new CreateSalesGoalRequest(
                "Deal count quota", repId, null, SalesGoal.Metric.DEAL_COUNT, new BigDecimal("5"), LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31));
        when(principal.getOrganizationId()).thenReturn(organizationId);
        when(principal.getId()).thenReturn(UUID.randomUUID());
        when(userRepository.findActiveById(repId)).thenReturn(Optional.of(activeUser(repId, organizationId)));
        when(salesGoalProgressRepository.sumWonBetween(any(), any(), any(), any(), any()))
                .thenReturn(new WonTotalsDto(4, new BigDecimal("999999")));

        SalesGoalDto result = service.create(principal, request);

        assertThat(result.actualValue()).isEqualByComparingTo("4");
        assertThat(result.percentComplete()).isEqualByComparingTo("80.0");
    }

    @Test
    void create_targetZero_percentCompleteIsZeroNotDivideByZero() {
        // @Positive on CreateSalesGoalRequest.targetValue is enforced by @Valid at the
        // controller layer, not by calling the service method directly - exercising the
        // service's own defensive divide-by-zero guard requires bypassing that here.
        CreateSalesGoalRequest request = new CreateSalesGoalRequest(
                "Weird quota", repId, null, SalesGoal.Metric.DEAL_COUNT, BigDecimal.ZERO, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31));
        when(principal.getOrganizationId()).thenReturn(organizationId);
        when(principal.getId()).thenReturn(UUID.randomUUID());
        when(userRepository.findActiveById(repId)).thenReturn(Optional.of(activeUser(repId, organizationId)));
        when(salesGoalProgressRepository.sumWonBetween(any(), any(), any(), any(), any())).thenReturn(new WonTotalsDto(0, BigDecimal.ZERO));

        SalesGoalDto result = service.create(principal, request);

        assertThat(result.percentComplete()).isEqualByComparingTo("0");
    }

    @Test
    void teamGoal_progressSumsCurrentTeamMembers_resolvedFreshOnEveryRead() {
        UUID teamId = UUID.randomUUID();
        UUID memberA = UUID.randomUUID();
        UUID memberB = UUID.randomUUID();
        CreateSalesGoalRequest request = new CreateSalesGoalRequest(
                "Team quota", null, teamId, SalesGoal.Metric.REVENUE, new BigDecimal("50000"), LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 31));
        when(principal.getOrganizationId()).thenReturn(organizationId);
        when(principal.getId()).thenReturn(UUID.randomUUID());
        when(teamRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(teamId, organizationId))
                .thenReturn(Optional.of(new com.aitrainercrm.platform.organization.entity.Team(organizationId, "Inbound", null)));
        when(userRepository.findIdsByOrganizationIdAndTeamId(organizationId, teamId)).thenReturn(List.of(memberA, memberB));
        when(salesGoalProgressRepository.sumWonBetween(
                        eq(organizationId), eq(Set.of(memberA, memberB)), eq(Opportunity.Stage.CLOSED_WON), any(), any()))
                .thenReturn(new WonTotalsDto(2, new BigDecimal("25000")));

        SalesGoalDto result = service.create(principal, request);

        assertThat(result.actualValue()).isEqualByComparingTo("25000");
        assertThat(result.percentComplete()).isEqualByComparingTo("50.0");
    }

    @Test
    void teamGoal_noCurrentMembers_skipsQueryEntirelyAndReportsZero() {
        UUID teamId = UUID.randomUUID();
        CreateSalesGoalRequest request = new CreateSalesGoalRequest(
                "Empty team quota", null, teamId, SalesGoal.Metric.REVENUE, new BigDecimal("50000"), LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 31));
        when(principal.getOrganizationId()).thenReturn(organizationId);
        when(principal.getId()).thenReturn(UUID.randomUUID());
        when(teamRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(teamId, organizationId))
                .thenReturn(Optional.of(new com.aitrainercrm.platform.organization.entity.Team(organizationId, "Empty Team", null)));
        when(userRepository.findIdsByOrganizationIdAndTeamId(organizationId, teamId)).thenReturn(List.of());

        SalesGoalDto result = service.create(principal, request);

        assertThat(result.actualValue()).isEqualByComparingTo("0");
        verify(salesGoalProgressRepository, never()).sumWonBetween(any(), any(), any(), any(), any());
    }

    @Test
    void myGoals_combinesOwnAndTeamGoals_skipsTeamLookupWhenCallerHasNoTeam() {
        when(principal.getOrganizationId()).thenReturn(organizationId);
        when(principal.getId()).thenReturn(repId);
        when(userRepository.findActiveById(repId)).thenReturn(Optional.of(activeUser(repId, organizationId))); // teamId null
        SalesGoal ownGoal = goal(SalesGoal.Metric.REVENUE, new BigDecimal("1000"));
        ownGoal.setOwnerUserId(repId);
        when(salesGoalRepository.findByOrganizationIdAndOwnerUserId(organizationId, repId)).thenReturn(List.of(ownGoal));
        when(salesGoalProgressRepository.sumWonBetween(any(), any(), any(), any(), any())).thenReturn(new WonTotalsDto(0, BigDecimal.ZERO));

        List<SalesGoalDto> result = service.myGoals(principal);

        assertThat(result).hasSize(1);
        verify(salesGoalRepository, never()).findByOrganizationIdAndTeamId(any(), any());
    }

    @Test
    void myGoals_callerOnATeam_alsoIncludesTeamGoals() {
        UUID teamId = UUID.randomUUID();
        when(principal.getOrganizationId()).thenReturn(organizationId);
        when(principal.getId()).thenReturn(repId);
        User me = activeUser(repId, organizationId);
        me.setTeamId(teamId);
        when(userRepository.findActiveById(repId)).thenReturn(Optional.of(me));
        when(salesGoalRepository.findByOrganizationIdAndOwnerUserId(organizationId, repId)).thenReturn(List.of());
        SalesGoal teamGoal = goal(SalesGoal.Metric.DEAL_COUNT, new BigDecimal("10"));
        teamGoal.setTeamId(teamId);
        when(salesGoalRepository.findByOrganizationIdAndTeamId(organizationId, teamId)).thenReturn(List.of(teamGoal));
        when(userRepository.findIdsByOrganizationIdAndTeamId(organizationId, teamId)).thenReturn(List.of(repId));
        when(salesGoalProgressRepository.sumWonBetween(any(), any(), any(), any(), any())).thenReturn(new WonTotalsDto(0, BigDecimal.ZERO));

        List<SalesGoalDto> result = service.myGoals(principal);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).teamId()).isEqualTo(teamId);
    }

    private User activeUser(UUID id, UUID organizationId) {
        User user = new User("rep-%s@example.com".formatted(id), "hash", "First", "Last");
        user.setId(id);
        user.setOrganizationId(organizationId);
        return user;
    }

    private SalesGoal goal(SalesGoal.Metric metric, BigDecimal targetValue) {
        SalesGoal goal = new SalesGoal(organizationId, "Goal", metric, targetValue, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        goal.setId(UUID.randomUUID());
        return goal;
    }
}
