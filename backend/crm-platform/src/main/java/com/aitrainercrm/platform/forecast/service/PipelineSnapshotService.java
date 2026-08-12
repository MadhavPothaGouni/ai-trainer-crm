package com.aitrainercrm.platform.forecast.service;

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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Captures and reads daily pipeline history - see V22's migration comment for why this is a
 * genuinely different concept from {@code report/}'s live aggregation, not a duplicate of it.
 * {@link #captureDaily} is this platform's second real {@code @Scheduled} job (after {@code
 * SlaEvaluationService#sweep}) and, like it, runs cross-tenant in a single pass rather than
 * looping per organization.
 *
 * <p>Read access reuses {@code REPORT:READ} rather than a resource of its own -
 * {@link #ownerFilter} is identical to {@code ReportService}'s, and both read methods here fold
 * the same {@code PipelineSnapshotRepository#findVisible} rows into two different shapes, the
 * same "one query, two derived views" reasoning {@code ReportService#repLeaderboard} already
 * established for {@code aggregateByOwnerAndStage}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PipelineSnapshotService {

    private final PipelineSnapshotRepository pipelineSnapshotRepository;
    private final PipelineCaptureRepository pipelineCaptureRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;

    @Scheduled(cron = "${crm.forecast.snapshot-cron:0 0 1 * * *}")
    public void captureDaily() {
        capture(LocalDate.now());
    }

    /** Split out from {@link #captureDaily} so a test (or a future backfill admin action) can capture an arbitrary date without waiting on the cron trigger. */
    @Transactional
    public int capture(LocalDate date) {
        List<OrgOwnerStageAggregateDto> rows = pipelineCaptureRepository.aggregateAllOrganizations();

        // Idempotent re-run: delete this date's rows for every organization, then re-insert fresh
        // ones from the current state of the opportunities table - see V22's migration comment
        // for why this is simpler than a find-or-create per (org, owner, stage) row.
        pipelineSnapshotRepository.deleteBySnapshotDate(date);

        List<PipelineSnapshot> snapshots = rows.stream()
                .map(row -> new PipelineSnapshot(
                        row.organizationId(), date, row.ownerId(), row.stage(), row.dealCount().intValue(), row.totalValue()))
                .toList();
        pipelineSnapshotRepository.saveAll(snapshots);

        log.info("Captured {} pipeline snapshot rows for {}", snapshots.size(), date);
        return snapshots.size();
    }

    /** Raw (date, owner, stage) rows visible to the caller within [from, to], oldest first. */
    @Transactional(readOnly = true)
    public List<PipelineSnapshotDto> listSnapshots(UserPrincipal principal, LocalDate from, LocalDate to) {
        assertValidRange(from, to);
        List<PipelineSnapshot> rows =
                pipelineSnapshotRepository.findVisible(principal.getOrganizationId(), from, to, ownerFilter(principal));
        return rows.stream().map(PipelineSnapshotDto::from).toList();
    }

    /** One point per day the caller has visibility into within [from, to] - see the class javadoc for why this folds the same rows {@link #listSnapshots} returns rather than a second query. */
    @Transactional(readOnly = true)
    public List<PipelineTrendPointDto> trend(UserPrincipal principal, LocalDate from, LocalDate to) {
        assertValidRange(from, to);
        List<PipelineSnapshot> rows =
                pipelineSnapshotRepository.findVisible(principal.getOrganizationId(), from, to, ownerFilter(principal));

        Map<LocalDate, DailyTally> byDate = new TreeMap<>();
        for (PipelineSnapshot row : rows) {
            DailyTally tally = byDate.computeIfAbsent(row.getSnapshotDate(), d -> new DailyTally());
            tally.dealCount += row.getDealCount();
            tally.totalValue = tally.totalValue.add(row.getTotalValue());
            tally.valueByStage.merge(row.getStage(), row.getTotalValue(), BigDecimal::add);
        }

        return byDate.entrySet().stream()
                .map(entry -> new PipelineTrendPointDto(
                        entry.getKey(), entry.getValue().dealCount, entry.getValue().totalValue, entry.getValue().valueByStage))
                .toList();
    }

    private void assertValidRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BusinessException("FORECAST_INVALID_RANGE", "'from' must not be after 'to'", HttpStatus.BAD_REQUEST);
        }
    }

    /** Identical to {@code ReportService#ownerFilter} - empty {@code Optional} (ORGANIZATION-scope access) maps to a {@code null} ownerIds param. */
    private Set<UUID> ownerFilter(UserPrincipal principal) {
        Optional<Set<UUID>> visible = scopeAuthorizationService.visibleOwnerIds(principal, Permission.Resource.REPORT, Permission.Action.READ);
        return visible.orElse(null);
    }

    /** Mutable running total for one day while folding {@code findVisible}'s rows - turned into an immutable {@link PipelineTrendPointDto} once every row for that day has been folded in. */
    private static final class DailyTally {
        int dealCount;
        BigDecimal totalValue = BigDecimal.ZERO;
        Map<Opportunity.Stage, BigDecimal> valueByStage = new EnumMap<>(Opportunity.Stage.class);
    }
}
