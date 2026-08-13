package com.aitrainercrm.platform.commission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.commission.dto.CreateCommissionPlanRequest;
import com.aitrainercrm.platform.commission.dto.UpdateCommissionPlanRequest;
import com.aitrainercrm.platform.commission.entity.CommissionPlan;
import com.aitrainercrm.platform.commission.repository.CommissionPlanRepository;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.organization.entity.Team;
import com.aitrainercrm.platform.organization.repository.TeamRepository;
import com.aitrainercrm.platform.user.entity.User;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommissionPlanServiceTest {

    @Mock private CommissionPlanRepository commissionPlanRepository;
    @Mock private UserRepository userRepository;
    @Mock private TeamRepository teamRepository;

    private CommissionPlanService service;

    private final UUID organizationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CommissionPlanService(commissionPlanRepository, userRepository, teamRepository);
    }

    @Test
    void create_neitherOwnerNorTeamSet_returns400() {
        CreateCommissionPlanRequest request =
                new CreateCommissionPlanRequest("Standard", null, null, CommissionPlan.RateType.PERCENTAGE, new BigDecimal("5"));

        assertThatThrownBy(() -> service.create(organizationId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Exactly one");
        verify(commissionPlanRepository, never()).save(any());
    }

    @Test
    void create_bothOwnerAndTeamSet_returns400() {
        CreateCommissionPlanRequest request = new CreateCommissionPlanRequest(
                "Standard", UUID.randomUUID(), UUID.randomUUID(), CommissionPlan.RateType.PERCENTAGE, new BigDecimal("5"));

        assertThatThrownBy(() -> service.create(organizationId, request)).isInstanceOf(BusinessException.class);
    }

    @Test
    void create_unknownOwner_throwsResourceNotFound() {
        UUID ownerId = UUID.randomUUID();
        when(userRepository.findActiveById(ownerId)).thenReturn(Optional.empty());
        CreateCommissionPlanRequest request =
                new CreateCommissionPlanRequest("Standard", ownerId, null, CommissionPlan.RateType.PERCENTAGE, new BigDecimal("5"));

        assertThatThrownBy(() -> service.create(organizationId, request)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_ownerFromAnotherOrganization_throwsResourceNotFound() {
        UUID ownerId = UUID.randomUUID();
        User otherOrgUser = new User("rep@example.com", "hash", "First", "Last");
        otherOrgUser.setOrganizationId(UUID.randomUUID());
        when(userRepository.findActiveById(ownerId)).thenReturn(Optional.of(otherOrgUser));
        CreateCommissionPlanRequest request =
                new CreateCommissionPlanRequest("Standard", ownerId, null, CommissionPlan.RateType.PERCENTAGE, new BigDecimal("5"));

        assertThatThrownBy(() -> service.create(organizationId, request)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_unknownTeam_throwsResourceNotFound() {
        UUID teamId = UUID.randomUUID();
        when(teamRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(teamId, organizationId)).thenReturn(Optional.empty());
        CreateCommissionPlanRequest request =
                new CreateCommissionPlanRequest("Team plan", null, teamId, CommissionPlan.RateType.FLAT_PER_DEAL, new BigDecimal("100"));

        assertThatThrownBy(() -> service.create(organizationId, request)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_validIndividualPlan_succeeds() {
        UUID ownerId = UUID.randomUUID();
        User owner = new User("rep@example.com", "hash", "First", "Last");
        owner.setOrganizationId(organizationId);
        when(userRepository.findActiveById(ownerId)).thenReturn(Optional.of(owner));
        CreateCommissionPlanRequest request =
                new CreateCommissionPlanRequest("Standard", ownerId, null, CommissionPlan.RateType.PERCENTAGE, new BigDecimal("5"));

        CommissionPlan result = service.create(organizationId, request);

        assertThat(result.getOwnerUserId()).isEqualTo(ownerId);
        assertThat(result.isTeamPlan()).isFalse();
        verify(commissionPlanRepository).save(result);
    }

    @Test
    void create_validTeamPlan_succeeds() {
        UUID teamId = UUID.randomUUID();
        when(teamRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(teamId, organizationId))
                .thenReturn(Optional.of(new Team(organizationId, "Closers", null)));
        CreateCommissionPlanRequest request =
                new CreateCommissionPlanRequest("Team plan", null, teamId, CommissionPlan.RateType.FLAT_PER_DEAL, new BigDecimal("150"));

        CommissionPlan result = service.create(organizationId, request);

        assertThat(result.isTeamPlan()).isTrue();
        verify(commissionPlanRepository).save(result);
    }

    @Test
    void update_unknownPlan_throwsResourceNotFound() {
        UUID planId = UUID.randomUUID();
        when(commissionPlanRepository.findByIdAndOrganizationId(planId, organizationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(
                        organizationId, planId,
                        new UpdateCommissionPlanRequest("X", UUID.randomUUID(), null, CommissionPlan.RateType.PERCENTAGE, BigDecimal.TEN, true)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
