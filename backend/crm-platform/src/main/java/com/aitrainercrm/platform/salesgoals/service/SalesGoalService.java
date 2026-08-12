package com.aitrainercrm.platform.salesgoals.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Two access patterns coexist here, deliberately - see V25's migration comment for the full
 * reasoning. {@link #list}/{@link #get}/{@link #create}/{@link #update}/{@link #delete} are
 * admin config, gated entirely by {@code SALES_GOAL:*:ORGANIZATION} in {@code
 * SalesGoalController} (no {@code ScopeAuthorizationService} call, same third-kind shape {@code
 * TerritoryRuleService}/{@code LeadScoringRuleService} use). {@link #myGoals} is the notification-
 * style fourth pattern instead: no permission check at all, just the caller's own id and current
 * team, the same shape {@code NotificationService} uses for a teammate's own inbox.
 *
 * <p>{@link #toDto} is where every returned goal picks up its live {@link SalesGoalDto#actualValue}/
 * {@link SalesGoalDto#percentComplete} - computed fresh on every call via {@link
 * SalesGoalProgressRepository}, never stored on {@link SalesGoal} itself.
 */
@Service
@RequiredArgsConstructor
public class SalesGoalService {

    private final SalesGoalRepository salesGoalRepository;
    private final SalesGoalProgressRepository salesGoalProgressRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<SalesGoalDto> list(UserPrincipal principal, Pageable pageable) {
        return salesGoalRepository.findByOrganizationIdOrderByPeriodStartDesc(principal.getOrganizationId(), pageable)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public SalesGoalDto get(UserPrincipal principal, UUID goalId) {
        return toDto(findOrThrow(principal.getOrganizationId(), goalId));
    }

    /** Every goal individually assigned to the caller, plus every goal assigned to their current team (skipped entirely when they have no team). No permission check - see this class's javadoc. */
    @Transactional(readOnly = true)
    public List<SalesGoalDto> myGoals(UserPrincipal principal) {
        UUID organizationId = principal.getOrganizationId();
        List<SalesGoal> own = salesGoalRepository.findByOrganizationIdAndOwnerUserId(organizationId, principal.getId());

        UUID myTeamId = userRepository.findActiveById(principal.getId()).map(User::getTeamId).orElse(null);
        List<SalesGoal> team = myTeamId == null
                ? List.of()
                : salesGoalRepository.findByOrganizationIdAndTeamId(organizationId, myTeamId);

        return Stream.concat(own.stream(), team.stream()).map(this::toDto).toList();
    }

    @Transactional
    public SalesGoalDto create(UserPrincipal principal, CreateSalesGoalRequest request) {
        UUID organizationId = principal.getOrganizationId();
        assertValidPeriod(request.periodStart(), request.periodEnd());
        assertExactlyOneTarget(organizationId, request.ownerUserId(), request.teamId());

        SalesGoal goal = new SalesGoal(
                organizationId, request.name(), request.metric(), request.targetValue(), request.periodStart(), request.periodEnd());
        goal.setOwnerUserId(request.ownerUserId());
        goal.setTeamId(request.teamId());
        salesGoalRepository.save(goal);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), organizationId, "SalesGoal", goal.getId()));
        return toDto(goal);
    }

    @Transactional
    public SalesGoalDto update(UserPrincipal principal, UUID goalId, UpdateSalesGoalRequest request) {
        UUID organizationId = principal.getOrganizationId();
        SalesGoal goal = findOrThrow(organizationId, goalId);
        assertValidPeriod(request.periodStart(), request.periodEnd());
        assertExactlyOneTarget(organizationId, request.ownerUserId(), request.teamId());

        goal.setName(request.name());
        goal.setOwnerUserId(request.ownerUserId());
        goal.setTeamId(request.teamId());
        goal.setMetric(request.metric());
        goal.setTargetValue(request.targetValue());
        goal.setPeriodStart(request.periodStart());
        goal.setPeriodEnd(request.periodEnd());
        salesGoalRepository.save(goal);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), organizationId, "SalesGoal", goal.getId()));
        return toDto(goal);
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID goalId) {
        SalesGoal goal = findOrThrow(principal.getOrganizationId(), goalId);
        salesGoalRepository.delete(goal);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "SalesGoal", goalId));
    }

    private SalesGoalDto toDto(SalesGoal goal) {
        Set<UUID> ownerIds = resolveOwnerIds(goal);
        WonTotalsDto totals = ownerIds.isEmpty()
                ? new WonTotalsDto(0, BigDecimal.ZERO)
                : salesGoalProgressRepository.sumWonBetween(
                        goal.getOrganizationId(), ownerIds, Opportunity.Stage.CLOSED_WON, goal.getPeriodStart(), goal.getPeriodEnd());

        BigDecimal actual = goal.getMetric() == SalesGoal.Metric.REVENUE ? totals.totalValue() : BigDecimal.valueOf(totals.dealCount());
        BigDecimal percent = goal.getTargetValue().signum() == 0
                ? BigDecimal.ZERO
                : actual.multiply(BigDecimal.valueOf(100)).divide(goal.getTargetValue(), 1, RoundingMode.HALF_UP);

        return SalesGoalDto.builder()
                .id(goal.getId())
                .name(goal.getName())
                .ownerUserId(goal.getOwnerUserId())
                .teamId(goal.getTeamId())
                .metric(goal.getMetric())
                .targetValue(goal.getTargetValue())
                .periodStart(goal.getPeriodStart())
                .periodEnd(goal.getPeriodEnd())
                .actualValue(actual)
                .percentComplete(percent)
                .createdAt(goal.getCreatedAt())
                .updatedAt(goal.getUpdatedAt())
                .build();
    }

    /** A team goal's progress sums every current member's won deals - membership is resolved fresh on every read, so someone who joined or left the team after the goal was created is picked up (or dropped) automatically, unlike TerritoryRule's round-robin cursor which only cares about membership at match time. */
    private Set<UUID> resolveOwnerIds(SalesGoal goal) {
        if (!goal.isTeamGoal()) {
            return Set.of(goal.getOwnerUserId());
        }
        return new LinkedHashSet<>(userRepository.findIdsByOrganizationIdAndTeamId(goal.getOrganizationId(), goal.getTeamId()));
    }

    private void assertValidPeriod(LocalDate periodStart, LocalDate periodEnd) {
        if (periodStart.isAfter(periodEnd)) {
            throw new BusinessException("SALES_GOAL_INVALID_PERIOD", "periodStart must not be after periodEnd", HttpStatus.BAD_REQUEST);
        }
    }

    private void assertExactlyOneTarget(UUID organizationId, UUID ownerUserId, UUID teamId) {
        boolean hasUser = ownerUserId != null;
        boolean hasTeam = teamId != null;
        if (hasUser == hasTeam) {
            throw new BusinessException(
                    "SALES_GOAL_INVALID_TARGET", "Exactly one of ownerUserId or teamId must be set", HttpStatus.BAD_REQUEST);
        }
        if (hasUser) {
            boolean exists = userRepository.findActiveById(ownerUserId).map(u -> organizationId.equals(u.getOrganizationId())).orElse(false);
            if (!exists) {
                throw new ResourceNotFoundException("User", ownerUserId);
            }
        } else {
            if (teamRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(teamId, organizationId).isEmpty()) {
                throw new ResourceNotFoundException("Team", teamId);
            }
        }
    }

    private SalesGoal findOrThrow(UUID organizationId, UUID goalId) {
        return salesGoalRepository.findByIdAndOrganizationId(goalId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("SalesGoal", goalId));
    }
}
