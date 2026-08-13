package com.aitrainercrm.platform.region.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.common.exception.BusinessException;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegionServiceTest {

    @Mock private RegionRepository regionRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private UserRepository userRepository;
    @Mock private OpportunityAnalyticsRepository opportunityAnalyticsRepository;

    private RegionService service;

    private final UUID organizationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RegionService(regionRepository, teamRepository, userRepository, opportunityAnalyticsRepository);
    }

    @Test
    void create_unknownParentId_throwsResourceNotFound() {
        UUID missingParentId = UUID.randomUUID();
        when(regionRepository.findActiveByIdAndOrganizationId(missingParentId, organizationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(organizationId, new CreateRegionRequest("US-West", missingParentId, null)))
                .isInstanceOf(com.aitrainercrm.platform.common.exception.ResourceNotFoundException.class);
        verify(regionRepository, never()).save(any());
    }

    @Test
    void create_noParent_createsARootRegion() {
        Region result = service.create(organizationId, new CreateRegionRequest("North America", null, "top level"));

        assertThat(result.isRoot()).isTrue();
        assertThat(result.getName()).isEqualTo("North America");
        verify(regionRepository).save(result);
    }

    @Test
    void update_settingParentToSelf_isRejected() {
        UUID regionId = UUID.randomUUID();
        Region region = region(regionId, "West", null);
        when(regionRepository.findActiveByIdAndOrganizationId(regionId, organizationId)).thenReturn(Optional.of(region));

        assertThatThrownBy(() -> service.update(organizationId, regionId, new UpdateRegionRequest("West", regionId, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("own parent");
        verify(regionRepository, never()).save(any());
    }

    @Test
    void update_settingParentToOwnDescendant_isRejected() {
        UUID parentId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        Region parent = region(parentId, "North America", null);
        Region child = region(childId, "US-West", parentId);
        when(regionRepository.findActiveByIdAndOrganizationId(parentId, organizationId)).thenReturn(Optional.of(parent));
        when(regionRepository.findActiveByIdAndOrganizationId(childId, organizationId)).thenReturn(Optional.of(child));
        when(regionRepository.findByOrganizationIdAndDeletedAtIsNullOrderByNameAsc(organizationId)).thenReturn(List.of(parent, child));

        // Trying to make "North America"'s parent be "US-West" - its own child - is a cycle.
        assertThatThrownBy(() -> service.update(organizationId, parentId, new UpdateRegionRequest("North America", childId, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("descendant of itself");
    }

    @Test
    void update_reparentingToAnUnrelatedRegion_succeeds() {
        UUID regionId = UUID.randomUUID();
        UUID newParentId = UUID.randomUUID();
        Region region = region(regionId, "US-Central", null);
        Region newParent = region(newParentId, "North America", null);
        when(regionRepository.findActiveByIdAndOrganizationId(regionId, organizationId)).thenReturn(Optional.of(region));
        when(regionRepository.findActiveByIdAndOrganizationId(newParentId, organizationId)).thenReturn(Optional.of(newParent));
        when(regionRepository.findByOrganizationIdAndDeletedAtIsNullOrderByNameAsc(organizationId)).thenReturn(List.of(region, newParent));

        Region result = service.update(organizationId, regionId, new UpdateRegionRequest("US-Central", newParentId, "moved"));

        assertThat(result.getParentRegionId()).isEqualTo(newParentId);
        verify(regionRepository).save(region);
    }

    @Test
    void delete_regionWithChildRegions_isRejected() {
        UUID regionId = UUID.randomUUID();
        when(regionRepository.findActiveByIdAndOrganizationId(regionId, organizationId)).thenReturn(Optional.of(region(regionId, "North America", null)));
        when(regionRepository.existsByOrganizationIdAndParentRegionIdAndDeletedAtIsNull(organizationId, regionId)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(organizationId, regionId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("child regions");
        verify(regionRepository, never()).save(any());
    }

    @Test
    void delete_regionWithATeamStillPointingAtIt_isRejected() {
        UUID regionId = UUID.randomUUID();
        when(regionRepository.findActiveByIdAndOrganizationId(regionId, organizationId)).thenReturn(Optional.of(region(regionId, "West", null)));
        when(regionRepository.existsByOrganizationIdAndParentRegionIdAndDeletedAtIsNull(organizationId, regionId)).thenReturn(false);
        when(teamRepository.existsByOrganizationIdAndRegionIdAndDeletedAtIsNull(organizationId, regionId)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(organizationId, regionId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Reassign");
    }

    @Test
    void delete_leafRegionWithNoTeams_succeeds() {
        UUID regionId = UUID.randomUUID();
        Region region = region(regionId, "West", null);
        when(regionRepository.findActiveByIdAndOrganizationId(regionId, organizationId)).thenReturn(Optional.of(region));
        when(regionRepository.existsByOrganizationIdAndParentRegionIdAndDeletedAtIsNull(organizationId, regionId)).thenReturn(false);
        when(teamRepository.existsByOrganizationIdAndRegionIdAndDeletedAtIsNull(organizationId, regionId)).thenReturn(false);

        service.delete(organizationId, regionId);

        assertThat(region.isDeleted()).isTrue();
        verify(regionRepository).save(region);
    }

    @Test
    void rollup_noTeamsInSubtree_skipsQueryEntirelyAndReportsZero() {
        UUID regionId = UUID.randomUUID();
        Region region = region(regionId, "West", null);
        when(regionRepository.findActiveByIdAndOrganizationId(regionId, organizationId)).thenReturn(Optional.of(region));
        when(regionRepository.findByOrganizationIdAndDeletedAtIsNullOrderByNameAsc(organizationId)).thenReturn(List.of(region));
        when(teamRepository.findIdsByOrganizationIdAndRegionIdIn(any(), any())).thenReturn(List.of());

        RegionRollupDto result = service.rollup(organizationId, regionId);

        assertThat(result.teamCount()).isZero();
        assertThat(result.userCount()).isZero();
        assertThat(result.openPipelineValue()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(opportunityAnalyticsRepository, never()).summarizeByStage(any(), anySet());
    }

    @Test
    void rollup_includesChildRegionTeams_andSeparatesOpenWonLost() {
        UUID parentId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID repId = UUID.randomUUID();
        Region parent = region(parentId, "North America", null);
        Region child = region(childId, "US-West", parentId);
        when(regionRepository.findActiveByIdAndOrganizationId(parentId, organizationId)).thenReturn(Optional.of(parent));
        when(regionRepository.findByOrganizationIdAndDeletedAtIsNullOrderByNameAsc(organizationId)).thenReturn(List.of(parent, child));
        when(teamRepository.findIdsByOrganizationIdAndRegionIdIn(any(), any())).thenReturn(List.of(teamId));
        when(userRepository.findIdsByOrganizationIdAndTeamId(organizationId, teamId)).thenReturn(List.of(repId));
        when(opportunityAnalyticsRepository.summarizeByStage(organizationId, java.util.Set.of(repId)))
                .thenReturn(List.of(
                        new PipelineStageSummaryDto(Opportunity.Stage.PROSPECTING, 2L, new BigDecimal("1000")),
                        new PipelineStageSummaryDto(Opportunity.Stage.NEGOTIATION, 1L, new BigDecimal("5000")),
                        new PipelineStageSummaryDto(Opportunity.Stage.CLOSED_WON, 3L, new BigDecimal("9000")),
                        new PipelineStageSummaryDto(Opportunity.Stage.CLOSED_LOST, 1L, new BigDecimal("2000"))));

        RegionRollupDto result = service.rollup(organizationId, parentId);

        assertThat(result.descendantRegionCount()).isEqualTo(1); // just "US-West", parent itself isn't counted as a descendant
        assertThat(result.teamCount()).isEqualTo(1);
        assertThat(result.userCount()).isEqualTo(1);
        assertThat(result.openOpportunityCount()).isEqualTo(3);
        assertThat(result.openPipelineValue()).isEqualByComparingTo("6000");
        assertThat(result.wonOpportunityCount()).isEqualTo(3);
        assertThat(result.wonValue()).isEqualByComparingTo("9000");
        assertThat(result.lostOpportunityCount()).isEqualTo(1);
        assertThat(result.lostValue()).isEqualByComparingTo("2000");
    }

    private Region region(UUID id, String name, UUID parentRegionId) {
        Region region = new Region(organizationId, name, parentRegionId);
        region.setId(id);
        return region;
    }
}
