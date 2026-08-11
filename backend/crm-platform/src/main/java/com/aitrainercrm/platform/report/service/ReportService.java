package com.aitrainercrm.platform.report.service;

import com.aitrainercrm.platform.lead.entity.Lead;
import com.aitrainercrm.platform.opportunity.entity.Opportunity;
import com.aitrainercrm.platform.report.dto.LeadFunnelStageDto;
import com.aitrainercrm.platform.report.dto.OwnerStageAggregateDto;
import com.aitrainercrm.platform.report.dto.PipelineStageSummaryDto;
import com.aitrainercrm.platform.report.dto.RepLeaderboardEntryDto;
import com.aitrainercrm.platform.report.repository.LeadAnalyticsRepository;
import com.aitrainercrm.platform.report.repository.OpportunityAnalyticsRepository;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.entity.User;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Read-only aggregation queries backing the reporting/analytics pages -
 * pipeline value by stage, the lead conversion funnel, and a per-rep
 * leaderboard. Every method here follows the same coarse-then-fine
 * authorization split as every other list endpoint in this codebase:
 * {@code ReportController}'s {@code @PreAuthorize} proves the caller holds
 * *some* level of {@code REPORT:READ}, and {@link #ownerFilter} (backed by
 * {@link ScopeAuthorizationService#visibleOwnerIds}) turns that into the
 * exact set of opportunity/lead owners they're allowed to see - the same
 * OWN/TEAM/ORGANIZATION scope narrowing that gates the underlying
 * Opportunity/Lead records themselves, just applied to an aggregate query
 * instead of a single row.
 *
 * <p>Unlike every CRUD module, there's no entity of its own here - these
 * methods aggregate directly over {@code Opportunity} and {@code Lead} via
 * the read-only {@code report.repository} interfaces, since a "report" is a
 * view over other modules' data, not a record with its own lifecycle.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final OpportunityAnalyticsRepository opportunityAnalyticsRepository;
    private final LeadAnalyticsRepository leadAnalyticsRepository;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;

    /** One row per {@link Opportunity.Stage}, zero-filled, sorted in pipeline order (PROSPECTING through CLOSED_LOST). */
    public List<PipelineStageSummaryDto> pipelineByStage(UserPrincipal principal) {
        Set<UUID> ownerIds = ownerFilter(principal);
        Map<Opportunity.Stage, PipelineStageSummaryDto> byStage = new EnumMap<>(Opportunity.Stage.class);
        opportunityAnalyticsRepository
                .summarizeByStage(principal.getOrganizationId(), ownerIds)
                .forEach(row -> byStage.put(row.stage(), row));

        return List.of(Opportunity.Stage.values()).stream()
                .map(stage -> byStage.getOrDefault(stage, new PipelineStageSummaryDto(stage, 0L, BigDecimal.ZERO)))
                .toList();
    }

    /** One row per {@link Lead.Status}, zero-filled, sorted in the order a lead is expected to move through the funnel. */
    public List<LeadFunnelStageDto> leadFunnel(UserPrincipal principal) {
        Set<UUID> ownerIds = ownerFilter(principal);
        Map<Lead.Status, LeadFunnelStageDto> byStatus = new EnumMap<>(Lead.Status.class);
        leadAnalyticsRepository.summarizeByStatus(principal.getOrganizationId(), ownerIds).forEach(row -> byStatus.put(row.status(), row));

        return List.of(Lead.Status.values()).stream()
                .map(status -> byStatus.getOrDefault(status, new LeadFunnelStageDto(status, 0L)))
                .toList();
    }

    /**
     * One row per opportunity owner visible to the caller, sorted by
     * {@code wonAmount} descending (ties broken by {@code openAmount}
     * descending) - reps working a big open pipeline still show up near the
     * top even before their first closed-won deal. Owners with zero
     * opportunities at all (open, won, or lost) don't appear - there's
     * nothing to rank.
     */
    public List<RepLeaderboardEntryDto> repLeaderboard(UserPrincipal principal) {
        Set<UUID> ownerIds = ownerFilter(principal);
        List<OwnerStageAggregateDto> rows = opportunityAnalyticsRepository.aggregateByOwnerAndStage(principal.getOrganizationId(), ownerIds);

        Map<UUID, RepTally> tallies = new LinkedHashMap<>();
        for (OwnerStageAggregateDto row : rows) {
            RepTally tally = tallies.computeIfAbsent(row.ownerId(), id -> new RepTally());
            if (row.stage() == Opportunity.Stage.CLOSED_WON) {
                tally.wonCount += row.opportunityCount();
                tally.wonAmount = tally.wonAmount.add(row.totalAmount());
            } else if (row.stage() == Opportunity.Stage.CLOSED_LOST) {
                tally.lostCount += row.opportunityCount();
            } else {
                tally.openCount += row.opportunityCount();
                tally.openAmount = tally.openAmount.add(row.totalAmount());
            }
        }

        Map<UUID, String> ownerNames = resolveOwnerNames(tallies.keySet());

        return tallies.entrySet().stream()
                .map(entry -> RepLeaderboardEntryDto.builder()
                        .ownerId(entry.getKey())
                        .ownerName(ownerNames.getOrDefault(entry.getKey(), "Unknown"))
                        .openCount(entry.getValue().openCount)
                        .openAmount(entry.getValue().openAmount)
                        .wonCount(entry.getValue().wonCount)
                        .wonAmount(entry.getValue().wonAmount)
                        .lostCount(entry.getValue().lostCount)
                        .build())
                .sorted(Comparator.comparing(RepLeaderboardEntryDto::wonAmount)
                        .thenComparing(RepLeaderboardEntryDto::openAmount)
                        .reversed())
                .toList();
    }

    /** Mutable running total for one owner while folding {@code aggregateByOwnerAndStage}'s rows - turned into an immutable {@link RepLeaderboardEntryDto} once every stage has been folded in. */
    private static final class RepTally {
        long openCount;
        BigDecimal openAmount = BigDecimal.ZERO;
        long wonCount;
        BigDecimal wonAmount = BigDecimal.ZERO;
        long lostCount;
    }

    /** Empty {@code Optional} (organization-wide access) maps to a {@code null} ownerIds param - see the analytics repositories' javadoc for why that means "no filter." */
    private Set<UUID> ownerFilter(UserPrincipal principal) {
        Optional<Set<UUID>> visible = scopeAuthorizationService.visibleOwnerIds(principal, Permission.Resource.REPORT, Permission.Action.READ);
        return visible.orElse(null);
    }

    private Map<UUID, String> resolveOwnerNames(Set<UUID> ownerIds) {
        Map<UUID, String> names = new LinkedHashMap<>();
        for (User user : userRepository.findAllById(ownerIds)) {
            names.put(user.getId(), user.getFullName());
        }
        return names;
    }
}
