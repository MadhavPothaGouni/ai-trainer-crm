package com.aitrainercrm.platform.leadscoring.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.lead.entity.Lead;
import com.aitrainercrm.platform.lead.repository.LeadRepository;
import com.aitrainercrm.platform.leadscoring.entity.LeadScoringRule;
import com.aitrainercrm.platform.leadscoring.repository.LeadScoringRuleRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Every dependency mocked - the listener invoked directly rather than through the event bus, same reasoning TerritoryAssignmentListenerTest documents. */
@ExtendWith(MockitoExtension.class)
class LeadScoringEngineTest {

    @Mock private LeadScoringRuleRepository leadScoringRuleRepository;
    @Mock private LeadRepository leadRepository;

    private LeadScoringEngine engine;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID actorUserId = UUID.randomUUID();
    private final UUID leadId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        engine = new LeadScoringEngine(leadScoringRuleRepository, leadRepository);
    }

    @Test
    void onRecordCreated_unrecognizedResourceType_neverQueriesRules() {
        engine.onRecordCreated(new CrmAuditEvents.RecordCreated(actorUserId, organizationId, "Contact", UUID.randomUUID()));

        verify(leadScoringRuleRepository, never()).findByOrganizationIdAndActiveTrue(any());
    }

    @Test
    void onRecordCreated_leadRecordVanished_doesNothing() {
        when(leadRepository.findActiveByIdAndOrganizationId(leadId, organizationId)).thenReturn(Optional.empty());

        engine.onRecordCreated(new CrmAuditEvents.RecordCreated(actorUserId, organizationId, "Lead", leadId));

        verify(leadScoringRuleRepository, never()).findByOrganizationIdAndActiveTrue(any());
    }

    @Test
    void onRecordCreated_noRulesConfigured_leavesZeroScoreUntouched() {
        Lead lead = lead(Lead.Source.WEBSITE, "Analytical Engines", "Ada", "ada@example.com");
        when(leadRepository.findActiveByIdAndOrganizationId(leadId, organizationId)).thenReturn(Optional.of(lead));
        when(leadScoringRuleRepository.findByOrganizationIdAndActiveTrue(organizationId)).thenReturn(List.of());

        engine.onRecordCreated(new CrmAuditEvents.RecordCreated(actorUserId, organizationId, "Lead", leadId));

        assertThat(lead.getScore()).isZero();
        verify(leadRepository, never()).save(any());
    }

    @Test
    void onRecordCreated_multipleMatchingRules_sumsAllOfThemUnlikeTerritorysFirstMatchWins() {
        LeadScoringRule sourceRule = rule(LeadScoringRule.MatchField.SOURCE, LeadScoringRule.MatchOperator.EQUALS, "WEBSITE", 10);
        LeadScoringRule titleRule = rule(LeadScoringRule.MatchField.TITLE, LeadScoringRule.MatchOperator.CONTAINS, "director", 25);
        LeadScoringRule nonMatchingRule = rule(LeadScoringRule.MatchField.COMPANY_NAME, LeadScoringRule.MatchOperator.EQUALS, "Nope Inc", 100);
        when(leadScoringRuleRepository.findByOrganizationIdAndActiveTrue(organizationId))
                .thenReturn(List.of(sourceRule, titleRule, nonMatchingRule));
        Lead lead = lead(Lead.Source.WEBSITE, "Analytical Engines", "Director of Sales", "ada@example.com");
        when(leadRepository.findActiveByIdAndOrganizationId(leadId, organizationId)).thenReturn(Optional.of(lead));

        engine.onRecordCreated(new CrmAuditEvents.RecordCreated(actorUserId, organizationId, "Lead", leadId));

        assertThat(lead.getScore()).isEqualTo(35);
        assertThat(sourceRule.getMatchCount()).isEqualTo(1);
        assertThat(titleRule.getMatchCount()).isEqualTo(1);
        assertThat(nonMatchingRule.getMatchCount()).isZero();
        verify(leadRepository).save(lead);
    }

    @Test
    void onRecordCreated_negativePointsRule_pushesScoreBelowZero() {
        LeadScoringRule penalty = rule(LeadScoringRule.MatchField.SOURCE, LeadScoringRule.MatchOperator.EQUALS, "COLD_CALL", -10);
        when(leadScoringRuleRepository.findByOrganizationIdAndActiveTrue(organizationId)).thenReturn(List.of(penalty));
        Lead lead = lead(Lead.Source.COLD_CALL, "Analytical Engines", null, null);
        when(leadRepository.findActiveByIdAndOrganizationId(leadId, organizationId)).thenReturn(Optional.of(lead));

        engine.onRecordCreated(new CrmAuditEvents.RecordCreated(actorUserId, organizationId, "Lead", leadId));

        assertThat(lead.getScore()).isEqualTo(-10);
    }

    @Test
    void onRecordCreated_emailDomainMatch() {
        LeadScoringRule rule = rule(LeadScoringRule.MatchField.EMAIL_DOMAIN, LeadScoringRule.MatchOperator.EQUALS, "acme.com", 15);
        when(leadScoringRuleRepository.findByOrganizationIdAndActiveTrue(organizationId)).thenReturn(List.of(rule));
        Lead lead = lead(Lead.Source.OTHER, null, null, "ada@acme.com");
        when(leadRepository.findActiveByIdAndOrganizationId(leadId, organizationId)).thenReturn(Optional.of(lead));

        engine.onRecordCreated(new CrmAuditEvents.RecordCreated(actorUserId, organizationId, "Lead", leadId));

        assertThat(lead.getScore()).isEqualTo(15);
    }

    @Test
    void onRecordCreated_emailWithNoAtSign_neverMatchesEmailDomainRule() {
        LeadScoringRule rule = rule(LeadScoringRule.MatchField.EMAIL_DOMAIN, LeadScoringRule.MatchOperator.CONTAINS, "acme", 15);
        when(leadScoringRuleRepository.findByOrganizationIdAndActiveTrue(organizationId)).thenReturn(List.of(rule));
        Lead lead = lead(Lead.Source.OTHER, null, null, "not-an-email");
        when(leadRepository.findActiveByIdAndOrganizationId(leadId, organizationId)).thenReturn(Optional.of(lead));

        engine.onRecordCreated(new CrmAuditEvents.RecordCreated(actorUserId, organizationId, "Lead", leadId));

        assertThat(lead.getScore()).isZero();
    }

    @Test
    void onRecordUpdated_reScoresJustLikeCreation() {
        LeadScoringRule rule = rule(LeadScoringRule.MatchField.SOURCE, LeadScoringRule.MatchOperator.EQUALS, "REFERRAL", 20);
        when(leadScoringRuleRepository.findByOrganizationIdAndActiveTrue(organizationId)).thenReturn(List.of(rule));
        Lead lead = lead(Lead.Source.REFERRAL, "Analytical Engines", null, null);
        when(leadRepository.findActiveByIdAndOrganizationId(leadId, organizationId)).thenReturn(Optional.of(lead));

        engine.onRecordUpdated(new CrmAuditEvents.RecordUpdated(actorUserId, organizationId, "Lead", leadId));

        assertThat(lead.getScore()).isEqualTo(20);
    }

    @Test
    void onRecordUpdated_scoreDropsToZeroAfterAllRulesAreDeactivated_stillGetsSaved() {
        Lead lead = lead(Lead.Source.WEBSITE, "Analytical Engines", null, null);
        lead.setScore(42); // stale from an earlier scoring pass, before every rule got deactivated
        when(leadRepository.findActiveByIdAndOrganizationId(leadId, organizationId)).thenReturn(Optional.of(lead));
        when(leadScoringRuleRepository.findByOrganizationIdAndActiveTrue(organizationId)).thenReturn(List.of());

        engine.onRecordUpdated(new CrmAuditEvents.RecordUpdated(actorUserId, organizationId, "Lead", leadId));

        assertThat(lead.getScore()).isZero();
        verify(leadRepository).save(lead);
    }

    @Test
    void onRecordUpdated_unrecognizedResourceType_doesNothing() {
        engine.onRecordUpdated(new CrmAuditEvents.RecordUpdated(actorUserId, organizationId, "Opportunity", UUID.randomUUID()));

        verify(leadRepository, never()).findActiveByIdAndOrganizationId(any(), any());
    }

    @Test
    void onRecordCreated_matchValueCaseInsensitive() {
        LeadScoringRule rule = rule(LeadScoringRule.MatchField.COMPANY_NAME, LeadScoringRule.MatchOperator.CONTAINS, "ENGINES", 5);
        when(leadScoringRuleRepository.findByOrganizationIdAndActiveTrue(organizationId)).thenReturn(List.of(rule));
        Lead lead = lead(Lead.Source.OTHER, "analytical engines inc", null, null);
        when(leadRepository.findActiveByIdAndOrganizationId(leadId, organizationId)).thenReturn(Optional.of(lead));

        engine.onRecordCreated(new CrmAuditEvents.RecordCreated(actorUserId, organizationId, "Lead", leadId));

        assertThat(lead.getScore()).isEqualTo(5);
    }

    private LeadScoringRule rule(LeadScoringRule.MatchField field, LeadScoringRule.MatchOperator operator, String value, int points) {
        LeadScoringRule rule = new LeadScoringRule(organizationId, "Rule", field, operator, value, points);
        rule.setId(UUID.randomUUID());
        return rule;
    }

    private Lead lead(Lead.Source source, String companyName, String title, String email) {
        Lead lead = new Lead(organizationId, "Ada", "Lovelace", UUID.randomUUID());
        lead.setId(leadId);
        lead.setSource(source);
        lead.setCompanyName(companyName);
        lead.setTitle(title);
        lead.setEmail(email);
        return lead;
    }
}
