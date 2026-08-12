package com.aitrainercrm.platform.forecast.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.forecast.dto.OrgOwnerStageAggregateDto;
import com.aitrainercrm.platform.forecast.dto.PipelineSnapshotDto;
import com.aitrainercrm.platform.forecast.dto.PipelineTrendPointDto;
import com.aitrainercrm.platform.forecast.entity.PipelineSnapshot;
import com.aitrainercrm.platform.forecast.repository.PipelineCaptureRepository;
import com.aitrainercrm.platform.forecast.repository.PipelineSnapshotRepository;
import com.aitrainercrm.platform.opportunity.entity.Opportunity;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Every dependency mocked - no Spring context, no database. {@link
 * PipelineSnapshotService#capture} and {@link PipelineSnapshotService#trend} are both exercised
 * directly rather than through the cron trigger or an HTTP round trip, the same reasoning {@code
 * SlaEvaluationServiceTest} uses for backdating a Ticket instead of waiting on a real clock -
 * verifying the aggregation/folding logic here doesn't need a running scheduler.
 */
@ExtendWith(MockitoExtension.class)
class PipelineSnapshotServiceTest {

    @Mock private PipelineSnapshotRepository pipelineSnapshotRepository;
    @Mock private PipelineCaptureRepository pipelineCaptureRepository;
    @Mock private ScopeAuthorizationService scopeAuthorizationService;
    @Mock private UserPrincipal principal;

    private PipelineSnapshotService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID ownerA = UUID.randomUUID();
    private final UUID ownerB = UUID.randomUUID();
    private final UUID otherOrgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PipelineSnapshotService(pipelineSnapshotRepository, pipelineCaptureRepository, scopeAuthorizationService);
    }

    @Test
    void capture_deletesThatDatesRowsThenReinsertsFromTheCurrentAggregate() {
        LocalDate date = LocalDate.of(2026, 8, 12);
        List<OrgOwnerStageAggregateDto> rows = List.of(
                new OrgOwnerStageAggregateDto(organizationId, ownerA, Opportunity.Stage.PROSPECTING, 3L, new BigDecimal("1500.00")),
                new OrgOwnerStageAggregateDto(otherOrgId, ownerB, Opportunity.Stage.CLOSED_WON, 1L, new BigDecimal("9000.00")));
        when(pipelineCaptureRepository.aggregateAllOrganizations()).thenReturn(rows);

        int captured = service.capture(date);

        assertThat(captured).isEqualTo(2);
        verify(pipelineSnapshotRepository).deleteBySnapshotDate(date);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PipelineSnapshot>> captor = ArgumentCaptor.forClass(List.class);
        verify(pipelineSnapshotRepository).saveAll(captor.capture());
        List<PipelineSnapshot> saved = captor.getValue();
        assertThat(saved).hasSize(2);

        PipelineSnapshot first = saved.get(0);
        assertThat(first.getOrganizationId()).isEqualTo(organizationId);
        assertThat(first.getSnapshotDate()).isEqualTo(date);
        assertThat(first.getOwnerId()).isEqualTo(ownerA);
        assertThat(first.getStage()).isEqualTo(Opportunity.Stage.PROSPECTING);
        assertThat(first.getDealCount()).isEqualTo(3);
        assertThat(first.getTotalValue()).isEqualByComparingTo("1500.00");

        PipelineSnapshot second = saved.get(1);
        assertThat(second.getOrganizationId()).isEqualTo(otherOrgId);
        assertThat(second.getTotalValue()).isEqualByComparingTo("9000.00");
    }

    @Test
    void capture_noOpportunitiesAnywhere_deletesAndSavesEmptyList() {
        when(pipelineCaptureRepository.aggregateAllOrganizations()).thenReturn(List.of());

        int captured = service.capture(LocalDate.now());

        assertThat(captured).isZero();
        verify(pipelineSnapshotRepository).saveAll(List.of());
    }

    @Test
    void listSnapshots_fromAfterTo_throwsWithoutQueryingTheRepository() {
        LocalDate from = LocalDate.of(2026, 8, 10);
        LocalDate to = LocalDate.of(2026, 8, 1);

        assertThatThrownBy(() -> service.listSnapshots(principal, from, to))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("'from' must not be after 'to'");
        verify(pipelineSnapshotRepository, never()).findVisible(any(), any(), any(), any());
    }

    @Test
    void listSnapshots_organizationScope_passesNullOwnerIdsAsNoFilter() {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 12);
        when(principal.getOrganizationId()).thenReturn(organizationId);
        when(scopeAuthorizationService.visibleOwnerIds(principal, Permission.Resource.REPORT, Permission.Action.READ))
                .thenReturn(Optional.empty());
        PipelineSnapshot row = snapshot(from, ownerA, Opportunity.Stage.NEGOTIATION, 2, "4200.00");
        when(pipelineSnapshotRepository.findVisible(eq(organizationId), eq(from), eq(to), isNull())).thenReturn(List.of(row));

        List<PipelineSnapshotDto> result = service.listSnapshots(principal, from, to);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).ownerId()).isEqualTo(ownerA);
        assertThat(result.get(0).totalValue()).isEqualByComparingTo("4200.00");
    }

    @Test
    void listSnapshots_ownScope_passesCallersOwnIdAsTheFilter() {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 12);
        UUID callerId = UUID.randomUUID();
        when(principal.getOrganizationId()).thenReturn(organizationId);
        when(scopeAuthorizationService.visibleOwnerIds(principal, Permission.Resource.REPORT, Permission.Action.READ))
                .thenReturn(Optional.of(Set.of(callerId)));
        when(pipelineSnapshotRepository.findVisible(organizationId, from, to, Set.of(callerId))).thenReturn(List.of());

        service.listSnapshots(principal, from, to);

        verify(pipelineSnapshotRepository).findVisible(organizationId, from, to, Set.of(callerId));
    }

    @Test
    void trend_foldsMultipleOwnersAndStagesOnTheSameDayIntoOnePoint() {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 12);
        LocalDate day = LocalDate.of(2026, 8, 5);
        when(principal.getOrganizationId()).thenReturn(organizationId);
        when(scopeAuthorizationService.visibleOwnerIds(principal, Permission.Resource.REPORT, Permission.Action.READ))
                .thenReturn(Optional.empty());
        when(pipelineSnapshotRepository.findVisible(organizationId, from, to, null))
                .thenReturn(List.of(
                        snapshot(day, ownerA, Opportunity.Stage.PROSPECTING, 2, "1000.00"),
                        snapshot(day, ownerB, Opportunity.Stage.PROSPECTING, 1, "500.00"),
                        snapshot(day, ownerA, Opportunity.Stage.CLOSED_WON, 1, "3000.00")));

        List<PipelineTrendPointDto> trend = service.trend(principal, from, to);

        assertThat(trend).hasSize(1);
        PipelineTrendPointDto point = trend.get(0);
        assertThat(point.date()).isEqualTo(day);
        assertThat(point.dealCount()).isEqualTo(4);
        assertThat(point.totalValue()).isEqualByComparingTo("4500.00");
        assertThat(point.valueByStage().get(Opportunity.Stage.PROSPECTING)).isEqualByComparingTo("1500.00");
        assertThat(point.valueByStage().get(Opportunity.Stage.CLOSED_WON)).isEqualByComparingTo("3000.00");
    }

    @Test
    void trend_multipleDays_returnsOneSortedPointPerDay() {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 12);
        LocalDate later = LocalDate.of(2026, 8, 10);
        LocalDate earlier = LocalDate.of(2026, 8, 2);
        when(principal.getOrganizationId()).thenReturn(organizationId);
        when(scopeAuthorizationService.visibleOwnerIds(principal, Permission.Resource.REPORT, Permission.Action.READ))
                .thenReturn(Optional.empty());
        // Rows deliberately out of chronological order - the folded result must still come back sorted.
        when(pipelineSnapshotRepository.findVisible(organizationId, from, to, null))
                .thenReturn(List.of(
                        snapshot(later, ownerA, Opportunity.Stage.PROSPECTING, 1, "100.00"),
                        snapshot(earlier, ownerA, Opportunity.Stage.PROSPECTING, 1, "200.00")));

        List<PipelineTrendPointDto> trend = service.trend(principal, from, to);

        assertThat(trend).extracting(PipelineTrendPointDto::date).containsExactly(earlier, later);
    }

    private PipelineSnapshot snapshot(LocalDate date, UUID ownerId, Opportunity.Stage stage, int dealCount, String totalValue) {
        return new PipelineSnapshot(organizationId, date, ownerId, stage, dealCount, new BigDecimal(totalValue));
    }
}
