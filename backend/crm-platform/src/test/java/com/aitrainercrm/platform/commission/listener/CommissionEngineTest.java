package com.aitrainercrm.platform.commission.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.commission.entity.CommissionPlan;
import com.aitrainercrm.platform.commission.entity.CommissionRecord;
import com.aitrainercrm.platform.commission.repository.CommissionPlanRepository;
import com.aitrainercrm.platform.commission.repository.CommissionRecordRepository;
import com.aitrainercrm.platform.opportunity.entity.Opportunity;
import com.aitrainercrm.platform.opportunity.repository.OpportunityRepository;
import com.aitrainercrm.platform.user.entity.User;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommissionEngineTest {

    @Mock private OpportunityRepository opportunityRepository;
    @Mock private CommissionPlanRepository commissionPlanRepository;
    @Mock private CommissionRecordRepository commissionRecordRepository;
    @Mock private UserRepository userRepository;

    private CommissionEngine engine;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID opportunityId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        engine = new CommissionEngine(opportunityRepository, commissionPlanRepository, commissionRecordRepository, userRepository);
    }

    @Test
    void onRecordUpdated_ignoresNonOpportunityResourceTypes() {
        engine.onRecordUpdated(new CrmAuditEvents.RecordUpdated(UUID.randomUUID(), organizationId, "Lead", opportunityId));

        verify(opportunityRepository, never()).findActiveByIdAndOrganizationId(any(), any());
    }

    @Test
    void onRecordUpdated_dealNotClosedWon_createsNothing() {
        Opportunity opportunity = opportunity(Opportunity.Stage.NEGOTIATION, new BigDecimal("1000"));
        when(opportunityRepository.findActiveByIdAndOrganizationId(opportunityId, organizationId)).thenReturn(Optional.of(opportunity));

        engine.onRecordUpdated(new CrmAuditEvents.RecordUpdated(UUID.randomUUID(), organizationId, "Opportunity", opportunityId));

        verify(commissionRecordRepository, never()).save(any());
    }

    @Test
    void onRecordUpdated_recordAlreadyExists_isANoOp_idempotent() {
        Opportunity opportunity = opportunity(Opportunity.Stage.CLOSED_WON, new BigDecimal("1000"));
        when(opportunityRepository.findActiveByIdAndOrganizationId(opportunityId, organizationId)).thenReturn(Optional.of(opportunity));
        when(commissionRecordRepository.existsByOpportunityId(opportunityId)).thenReturn(true);

        engine.onRecordUpdated(new CrmAuditEvents.RecordUpdated(UUID.randomUUID(), organizationId, "Opportunity", opportunityId));

        verify(commissionRecordRepository, never()).save(any());
        verify(commissionPlanRepository, never()).findByOrganizationIdAndOwnerUserIdAndActiveTrueOrderByNameAsc(any(), any());
    }

    @Test
    void onRecordUpdated_noPlanAtAll_createsNothing() {
        Opportunity opportunity = opportunity(Opportunity.Stage.CLOSED_WON, new BigDecimal("1000"));
        when(opportunityRepository.findActiveByIdAndOrganizationId(opportunityId, organizationId)).thenReturn(Optional.of(opportunity));
        when(commissionRecordRepository.existsByOpportunityId(opportunityId)).thenReturn(false);
        when(commissionPlanRepository.findByOrganizationIdAndOwnerUserIdAndActiveTrueOrderByNameAsc(organizationId, ownerId))
                .thenReturn(List.of());
        when(userRepository.findActiveById(ownerId)).thenReturn(Optional.of(user(null)));

        engine.onRecordUpdated(new CrmAuditEvents.RecordUpdated(UUID.randomUUID(), organizationId, "Opportunity", opportunityId));

        verify(commissionRecordRepository, never()).save(any());
    }

    @Test
    void onRecordUpdated_individualPlanWinsOverTeamPlan() {
        Opportunity opportunity = opportunity(Opportunity.Stage.CLOSED_WON, new BigDecimal("10000"));
        when(opportunityRepository.findActiveByIdAndOrganizationId(opportunityId, organizationId)).thenReturn(Optional.of(opportunity));
        when(commissionRecordRepository.existsByOpportunityId(opportunityId)).thenReturn(false);
        CommissionPlan individualPlan = plan(CommissionPlan.RateType.PERCENTAGE, new BigDecimal("5.00"));
        when(commissionPlanRepository.findByOrganizationIdAndOwnerUserIdAndActiveTrueOrderByNameAsc(organizationId, ownerId))
                .thenReturn(List.of(individualPlan));

        engine.onRecordUpdated(new CrmAuditEvents.RecordUpdated(UUID.randomUUID(), organizationId, "Opportunity", opportunityId));

        ArgumentCaptor<CommissionRecord> captor = ArgumentCaptor.forClass(CommissionRecord.class);
        verify(commissionRecordRepository).save(captor.capture());
        assertThat(captor.getValue().getCommissionAmount()).isEqualByComparingTo("500.00");
        // Never even looks up the owner's team when an individual plan already resolved it.
        verify(userRepository, never()).findActiveById(any());
    }

    @Test
    void onRecordUpdated_noIndividualPlan_fallsBackToTeamPlan() {
        UUID teamId = UUID.randomUUID();
        Opportunity opportunity = opportunity(Opportunity.Stage.CLOSED_WON, new BigDecimal("2000"));
        when(opportunityRepository.findActiveByIdAndOrganizationId(opportunityId, organizationId)).thenReturn(Optional.of(opportunity));
        when(commissionRecordRepository.existsByOpportunityId(opportunityId)).thenReturn(false);
        when(commissionPlanRepository.findByOrganizationIdAndOwnerUserIdAndActiveTrueOrderByNameAsc(organizationId, ownerId))
                .thenReturn(List.of());
        when(userRepository.findActiveById(ownerId)).thenReturn(Optional.of(user(teamId)));
        CommissionPlan teamPlan = plan(CommissionPlan.RateType.FLAT_PER_DEAL, new BigDecimal("150.00"));
        when(commissionPlanRepository.findByOrganizationIdAndTeamIdAndActiveTrueOrderByNameAsc(organizationId, teamId))
                .thenReturn(List.of(teamPlan));

        engine.onRecordUpdated(new CrmAuditEvents.RecordUpdated(UUID.randomUUID(), organizationId, "Opportunity", opportunityId));

        ArgumentCaptor<CommissionRecord> captor = ArgumentCaptor.forClass(CommissionRecord.class);
        verify(commissionRecordRepository).save(captor.capture());
        assertThat(captor.getValue().getCommissionAmount()).isEqualByComparingTo("150.00");
        assertThat(captor.getValue().getRateType()).isEqualTo(CommissionPlan.RateType.FLAT_PER_DEAL);
    }

    @Test
    void onRecordUpdated_percentageRounding_halfUpToTwoDecimals() {
        Opportunity opportunity = opportunity(Opportunity.Stage.CLOSED_WON, new BigDecimal("333.33"));
        when(opportunityRepository.findActiveByIdAndOrganizationId(opportunityId, organizationId)).thenReturn(Optional.of(opportunity));
        when(commissionRecordRepository.existsByOpportunityId(opportunityId)).thenReturn(false);
        CommissionPlan individualPlan = plan(CommissionPlan.RateType.PERCENTAGE, new BigDecimal("7.5"));
        when(commissionPlanRepository.findByOrganizationIdAndOwnerUserIdAndActiveTrueOrderByNameAsc(organizationId, ownerId))
                .thenReturn(List.of(individualPlan));

        engine.onRecordUpdated(new CrmAuditEvents.RecordUpdated(UUID.randomUUID(), organizationId, "Opportunity", opportunityId));

        ArgumentCaptor<CommissionRecord> captor = ArgumentCaptor.forClass(CommissionRecord.class);
        verify(commissionRecordRepository).save(captor.capture());
        // 333.33 * 0.075 = 24.99975 -> rounds to 25.00
        assertThat(captor.getValue().getCommissionAmount()).isEqualByComparingTo("25.00");
    }

    private Opportunity opportunity(Opportunity.Stage stage, BigDecimal amount) {
        Opportunity opportunity = new Opportunity(organizationId, UUID.randomUUID(), "Deal", ownerId);
        opportunity.setId(opportunityId);
        opportunity.setStage(stage);
        opportunity.setAmount(amount);
        return opportunity;
    }

    private CommissionPlan plan(CommissionPlan.RateType rateType, BigDecimal rate) {
        CommissionPlan plan = new CommissionPlan(organizationId, "Plan", ownerId, null, rateType, rate);
        plan.setId(UUID.randomUUID());
        return plan;
    }

    private User user(UUID teamId) {
        User user = new User("rep@example.com", "hash", "First", "Last");
        user.setId(ownerId);
        user.setOrganizationId(organizationId);
        user.setTeamId(teamId);
        return user;
    }
}
