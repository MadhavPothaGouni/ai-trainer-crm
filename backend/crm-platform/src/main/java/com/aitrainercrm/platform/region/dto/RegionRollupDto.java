package com.aitrainercrm.platform.region.dto;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;

/**
 * A Region's numbers computed live, rolled up across itself and every descendant Region -
 * {@code RegionService#rollup}'s whole reason for existing. Deliberately live, never materialized,
 * the same choice {@code SalesGoalService} made over {@code forecast/}'s snapshot approach: a
 * region hierarchy is edited far less often than it's read, and there's no "period" here that ever
 * closes the way a SalesGoal's does, so there's nothing that would ever go stale enough to be worth
 * snapshotting.
 */
@Builder
public record RegionRollupDto(
        UUID regionId,
        String regionName,
        int descendantRegionCount,
        int teamCount,
        int userCount,
        long openOpportunityCount,
        BigDecimal openPipelineValue,
        long wonOpportunityCount,
        BigDecimal wonValue,
        long lostOpportunityCount,
        BigDecimal lostValue) {
}
