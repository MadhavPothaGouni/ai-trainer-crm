package com.aitrainercrm.platform.region.service;

import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.opportunity.entity.Opportunity;
import com.aitrainercrm.platform.organization.repository.TeamRepository;
import com.aitrainercrm.platform.region.dto.CreateRegionRequest;
import com.aitrainercrm.platform.region.dto.RegionRollupDto;
import com.aitrainercrm.platform.region.dto.UpdateRegionRequest;
import com.aitrainercrm.platform.region.entity.Region;
import com.aitrainercrm.platform.region.repository.RegionRepository;
import com.aitrainercrm.platform.report.dto.PipelineStageSummaryDto;
import com.aitrainercrm.platform.report.repository.OpportunityAnalyticsRepository;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Region tree itself, plus {@link #rollup} - see V28's migration comment and {@link
 * Region}'s javadoc for how this differs from {@code territory/}'s TerritoryRule. No {@link
 * com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService} call anywhere:
 * REGION only has ORGANIZATION scope seeded (V28), the same third-kind admin-config shape
 * SlaPolicy/TerritoryRule/LeadScoringRule/SalesGoal already use.
 *
 * <p>{@link #assertNoCycle} is the one piece of real logic a database constraint can't express for
 * a self-referencing tree: a plain foreign key on {@code parent_region_id} would happily accept
 * "B's parent is A, A's parent is B," silently turning the tree into a cycle that {@link
 * #getDescendantRegionIds} would then loop on forever. Every create/update walks the *proposed*
 * parent's own ancestor chain in memory (using the same full-org region list {@link #rollup} builds
 * its traversal map from) and rejects the request if the region being saved would appear in its own
 * ancestry.
 */
@Service
@RequiredArgsConstructor
public class RegionService {

    private final RegionRepository regionRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final OpportunityAnalyticsRepository opportunityAnalyticsRepository;

    @Transactional(readOnly = true)
    public List<Region> list(UUID organizationId) {
        return regionRepository.findByOrganizationIdAndDeletedAtIsNullOrderByNameAsc(organizationId);
    }

    @Transactional(readOnly = true)
    public Region get(UUID organizationId, UUID regionId) {
        return findOrThrow(organizationId, regionId);
    }

    @Transactional
    public Region create(UUID organizationId, CreateRegionRequest request) {
        assertParentExists(organizationId, request.parentRegionId());

        Region region = new Region(organizationId, request.name(), request.parentRegionId());
        region.setDescription(request.description());
        regionRepository.save(region);
        return region;
    }

    @Transactional
    public Region update(UUID organizationId, UUID regionId, UpdateRegionRequest request) {
        Region region = findOrThrow(organizationId, regionId);
        assertParentExists(organizationId, request.parentRegionId());
        assertNoCycle(organizationId, regionId, request.parentRegionId());

        region.setName(request.name());
        region.setParentRegionId(request.parentRegionId());
        region.setDescription(request.description());
        regionRepository.save(region);
        return region;
    }

    /**
     * Requires the region to be a leaf (no child regions) and unclaimed (no Team currently points
     * at it) before it can go away - the caller has to explicitly reparent children and reassign
     * teams first, rather than this method silently cascading either. Same conservative "make the
     * caller do the reassignment" reasoning {@code TeamService#delete}'s own javadoc documents for
     * why deleting a team never touches the users on it, just applied one level earlier here: an
     * automatic cascade could quietly orphan a whole subtree's worth of rollup numbers in one call.
     */
    @Transactional
    public void delete(UUID organizationId, UUID regionId) {
        Region region = findOrThrow(organizationId, regionId);

        if (regionRepository.existsByOrganizationIdAndParentRegionIdAndDeletedAtIsNull(organizationId, regionId)) {
            throw new BusinessException(
                    "REGION_HAS_CHILDREN", "Reparent or delete this region's child regions first.", HttpStatus.CONFLICT);
        }
        if (teamRepository.existsByOrganizationIdAndRegionIdAndDeletedAtIsNull(organizationId, regionId)) {
            throw new BusinessException(
                    "REGION_HAS_TEAMS", "Reassign every team pointing at this region first.", HttpStatus.CONFLICT);
        }

        region.setDeletedAt(Instant.now());
        regionRepository.save(region);
    }

    /**
     * Rolls a Region's Opportunity numbers up across itself and every descendant Region: walk the
     * tree down from {@code regionId}, collect every Team pointing at any node in that subtree,
     * then every currently-active user on those teams, then aggregate real Opportunity rows for
     * that owner set via {@code report/}'s existing {@code OpportunityAnalyticsRepository} -
     * deliberately reused rather than adding a third near-identical "second repository over
     * Opportunity" (after {@code report/}'s own and {@code salesgoals/}'s
     * SalesGoalProgressRepository): {@code summarizeByStage} already returns exactly the
     * per-stage count/sum shape this needs, with no period bound to add, unlike SalesGoal's
     * necessarily period-scoped query.
     */
    @Transactional(readOnly = true)
    public RegionRollupDto rollup(UUID organizationId, UUID regionId) {
        Region region = findOrThrow(organizationId, regionId);
        Set<UUID> descendantIds = getDescendantRegionIds(organizationId, regionId);

        List<UUID> teamIds = teamRepository.findIdsByOrganizationIdAndRegionIdIn(organizationId, new ArrayList<>(descendantIds));
        Set<UUID> ownerIds = new HashSet<>();
        for (UUID teamId : teamIds) {
            ownerIds.addAll(userRepository.findIdsByOrganizationIdAndTeamId(organizationId, teamId));
        }

        RegionRollupDto.RegionRollupDtoBuilder builder = RegionRollupDto.builder()
                .regionId(region.getId())
                .regionName(region.getName())
                .descendantRegionCount(descendantIds.size() - 1)
                .teamCount(teamIds.size())
                .userCount(ownerIds.size());

        if (ownerIds.isEmpty()) {
            return builder
                    .openOpportunityCount(0)
                    .openPipelineValue(BigDecimal.ZERO)
                    .wonOpportunityCount(0)
                    .wonValue(BigDecimal.ZERO)
                    .lostOpportunityCount(0)
                    .lostValue(BigDecimal.ZERO)
                    .build();
        }

        List<PipelineStageSummaryDto> rows = opportunityAnalyticsRepository.summarizeByStage(organizationId, ownerIds);

        long openCount = 0;
        BigDecimal openValue = BigDecimal.ZERO;
        long wonCount = 0;
        BigDecimal wonValue = BigDecimal.ZERO;
        long lostCount = 0;
        BigDecimal lostValue = BigDecimal.ZERO;
        for (PipelineStageSummaryDto row : rows) {
            long count = row.opportunityCount() == null ? 0 : row.opportunityCount();
            BigDecimal amount = row.totalAmount() == null ? BigDecimal.ZERO : row.totalAmount();
            if (row.stage() == Opportunity.Stage.CLOSED_WON) {
                wonCount = count;
                wonValue = amount;
            } else if (row.stage() == Opportunity.Stage.CLOSED_LOST) {
                lostCount = count;
                lostValue = amount;
            } else {
                openCount += count;
                openValue = openValue.add(amount);
            }
        }

        return builder
                .openOpportunityCount(openCount)
                .openPipelineValue(openValue)
                .wonOpportunityCount(wonCount)
                .wonValue(wonValue)
                .lostOpportunityCount(lostCount)
                .lostValue(lostValue)
                .build();
    }

    /** {@code regionId} itself is always included, so callers never special-case "the region and
     * its descendants" vs. just "its descendants." */
    private Set<UUID> getDescendantRegionIds(UUID organizationId, UUID regionId) {
        Map<UUID, List<UUID>> childrenByParent = buildChildrenMap(organizationId);

        Set<UUID> result = new HashSet<>();
        Deque<UUID> queue = new ArrayDeque<>();
        queue.add(regionId);
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            if (!result.add(current)) continue;
            queue.addAll(childrenByParent.getOrDefault(current, List.of()));
        }
        return result;
    }

    private Map<UUID, List<UUID>> buildChildrenMap(UUID organizationId) {
        Map<UUID, List<UUID>> childrenByParent = new HashMap<>();
        for (Region candidate : regionRepository.findByOrganizationIdAndDeletedAtIsNullOrderByNameAsc(organizationId)) {
            if (candidate.getParentRegionId() != null) {
                childrenByParent.computeIfAbsent(candidate.getParentRegionId(), key -> new ArrayList<>()).add(candidate.getId());
            }
        }
        return childrenByParent;
    }

    private void assertNoCycle(UUID organizationId, UUID regionId, UUID proposedParentId) {
        if (proposedParentId == null) return;
        if (proposedParentId.equals(regionId)) {
            throw new BusinessException("REGION_CYCLE", "A region cannot be its own parent.", HttpStatus.BAD_REQUEST);
        }
        if (getDescendantRegionIds(organizationId, regionId).contains(proposedParentId)) {
            throw new BusinessException(
                    "REGION_CYCLE", "That would make this region a descendant of itself.", HttpStatus.BAD_REQUEST);
        }
    }

    private void assertParentExists(UUID organizationId, UUID parentRegionId) {
        if (parentRegionId == null) return;
        if (regionRepository.findActiveByIdAndOrganizationId(parentRegionId, organizationId).isEmpty()) {
            throw new ResourceNotFoundException("Region", parentRegionId);
        }
    }

    private Region findOrThrow(UUID organizationId, UUID regionId) {
        return regionRepository.findActiveByIdAndOrganizationId(regionId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Region", regionId));
    }
}
