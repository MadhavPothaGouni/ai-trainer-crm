package com.aitrainercrm.platform.territory.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.account.entity.Account;
import com.aitrainercrm.platform.account.repository.AccountRepository;
import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.lead.entity.Lead;
import com.aitrainercrm.platform.lead.repository.LeadRepository;
import com.aitrainercrm.platform.territory.entity.TerritoryRule;
import com.aitrainercrm.platform.territory.repository.TerritoryRuleRepository;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Every dependency mocked, and the listener invoked directly (not through the event bus, unlike
 * {@code TerritoryRuleIntegrationTest}) so every branch of the matching/round-robin algorithm can
 * be exercised deterministically - real UUID sort order for round-robin is exactly why the ids
 * below are handed out from a fixed, pre-sorted array rather than {@code UUID.randomUUID()}.
 */
@ExtendWith(MockitoExtension.class)
class TerritoryAssignmentListenerTest {

    @Mock private TerritoryRuleRepository territoryRuleRepository;
    @Mock private LeadRepository leadRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher events;

    private TerritoryAssignmentListener listener;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID actorUserId = UUID.randomUUID();
    private final UUID leadId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();

    // Pre-sorted ascending by UUID.compareTo, confirmed once in setUp() rather than assumed.
    private final UUID userA = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID userB = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final UUID userC = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @BeforeEach
    void setUp() {
        listener = new TerritoryAssignmentListener(territoryRuleRepository, leadRepository, accountRepository, userRepository, events);
        assertThat(List.of(userA, userB, userC)).isSorted();
    }

    @Test
    void onRecordCreated_unrecognizedResourceType_neverQueriesRules() {
        listener.onRecordCreated(new CrmAuditEvents.RecordCreated(actorUserId, organizationId, "Opportunity", UUID.randomUUID()));

        verify(territoryRuleRepository, never()).findByOrganizationIdAndTargetResourceAndActiveTrueOrderByPriorityAsc(any(), any());
    }

    @Test
    void onRecordCreated_noActiveRulesForResource_doesNothing() {
        when(territoryRuleRepository.findByOrganizationIdAndTargetResourceAndActiveTrueOrderByPriorityAsc(
                        organizationId, TerritoryRule.TargetResource.LEAD))
                .thenReturn(List.of());

        listener.onRecordCreated(new CrmAuditEvents.RecordCreated(actorUserId, organizationId, "Lead", leadId));

        verify(leadRepository, never()).findActiveByIdAndOrganizationId(any(), any());
    }

    @Test
    void onRecordCreated_leadRecordVanished_doesNothing() {
        TerritoryRule rule = leadRule(TerritoryRule.MatchField.SOURCE, TerritoryRule.MatchOperator.EQUALS, "WEBSITE", userA, null);
        stubLeadRules(rule);
        when(leadRepository.findActiveByIdAndOrganizationId(leadId, organizationId)).thenReturn(Optional.empty());

        listener.onRecordCreated(new CrmAuditEvents.RecordCreated(actorUserId, organizationId, "Lead", leadId));

        verify(leadRepository, never()).save(any());
    }

    @Test
    void onRecordCreated_leadMatchesBySourceEquals_assignsDirectUserAndRecordsMatch() {
        TerritoryRule rule = leadRule(TerritoryRule.MatchField.SOURCE, TerritoryRule.MatchOperator.EQUALS, "WEBSITE", userA, null);
        stubLeadRules(rule);
        Lead lead = lead(Lead.Source.WEBSITE, "Analytical Engines");
        when(leadRepository.findActiveByIdAndOrganizationId(leadId, organizationId)).thenReturn(Optional.of(lead));

        listener.onRecordCreated(new CrmAuditEvents.RecordCreated(actorUserId, organizationId, "Lead", leadId));

        assertThat(lead.getOwnerId()).isEqualTo(userA);
        verify(leadRepository).save(lead);
        assertThat(rule.getMatchCount()).isEqualTo(1);
        assertThat(rule.getLastMatchedAt()).isNotNull();
        verify(events).publishEvent(any(CrmAuditEvents.RecordAssigned.class));
    }

    @Test
    void onRecordCreated_firstRuleDoesNotMatch_fallsThroughToSecondRuleByCompanyNameContains() {
        TerritoryRule ruleOne = leadRule(TerritoryRule.MatchField.SOURCE, TerritoryRule.MatchOperator.EQUALS, "REFERRAL", userA, null);
        TerritoryRule ruleTwo = leadRule(TerritoryRule.MatchField.COMPANY_NAME, TerritoryRule.MatchOperator.CONTAINS, "engines", userB, null);
        stubLeadRules(ruleOne, ruleTwo);
        Lead lead = lead(Lead.Source.WEBSITE, "Analytical Engines Inc");
        when(leadRepository.findActiveByIdAndOrganizationId(leadId, organizationId)).thenReturn(Optional.of(lead));

        listener.onRecordCreated(new CrmAuditEvents.RecordCreated(actorUserId, organizationId, "Lead", leadId));

        assertThat(lead.getOwnerId()).isEqualTo(userB);
        assertThat(ruleOne.getMatchCount()).isZero();
        assertThat(ruleTwo.getMatchCount()).isEqualTo(1);
    }

    @Test
    void onRecordCreated_noRuleMatches_doesNotTouchOwner() {
        TerritoryRule rule = leadRule(TerritoryRule.MatchField.SOURCE, TerritoryRule.MatchOperator.EQUALS, "COLD_CALL", userA, null);
        stubLeadRules(rule);
        Lead lead = lead(Lead.Source.WEBSITE, "Analytical Engines");
        UUID originalOwner = lead.getOwnerId();
        when(leadRepository.findActiveByIdAndOrganizationId(leadId, organizationId)).thenReturn(Optional.of(lead));

        listener.onRecordCreated(new CrmAuditEvents.RecordCreated(actorUserId, organizationId, "Lead", leadId));

        assertThat(lead.getOwnerId()).isEqualTo(originalOwner);
        verify(leadRepository, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void onRecordCreated_accountMatchesByIndustryContains_assignsDirectUser() {
        TerritoryRule rule = new TerritoryRule(
                organizationId, "Tech accounts", TerritoryRule.TargetResource.ACCOUNT,
                TerritoryRule.MatchField.INDUSTRY, TerritoryRule.MatchOperator.CONTAINS, "software");
        rule.setId(UUID.randomUUID());
        rule.setAssignToUserId(userA);
        when(territoryRuleRepository.findByOrganizationIdAndTargetResourceAndActiveTrueOrderByPriorityAsc(
                        organizationId, TerritoryRule.TargetResource.ACCOUNT))
                .thenReturn(List.of(rule));
        Account account = new Account(organizationId, "Acme Rockets", UUID.randomUUID());
        account.setIndustry("Enterprise Software");
        when(accountRepository.findActiveByIdAndOrganizationId(accountId, organizationId)).thenReturn(Optional.of(account));

        listener.onRecordCreated(new CrmAuditEvents.RecordCreated(actorUserId, organizationId, "Account", accountId));

        assertThat(account.getOwnerId()).isEqualTo(userA);
        verify(accountRepository).save(account);
    }

    @Test
    void onRecordCreated_teamAssignmentNoCursorYet_assignsFirstMemberInSortedOrder() {
        UUID teamId = UUID.randomUUID();
        TerritoryRule rule = leadRule(TerritoryRule.MatchField.SOURCE, TerritoryRule.MatchOperator.EQUALS, "WEBSITE", null, teamId);
        stubLeadRules(rule);
        Lead lead = lead(Lead.Source.WEBSITE, "Analytical Engines");
        when(leadRepository.findActiveByIdAndOrganizationId(leadId, organizationId)).thenReturn(Optional.of(lead));
        when(userRepository.findIdsByOrganizationIdAndTeamId(organizationId, teamId)).thenReturn(List.of(userC, userA, userB));

        listener.onRecordCreated(new CrmAuditEvents.RecordCreated(actorUserId, organizationId, "Lead", leadId));

        assertThat(lead.getOwnerId()).isEqualTo(userA);
        assertThat(rule.getLastAssignedUserId()).isEqualTo(userA);
    }

    @Test
    void onRecordCreated_teamAssignmentWithCursor_advancesToNextMemberInSortedOrder() {
        UUID teamId = UUID.randomUUID();
        TerritoryRule rule = leadRule(TerritoryRule.MatchField.SOURCE, TerritoryRule.MatchOperator.EQUALS, "WEBSITE", null, teamId);
        rule.setLastAssignedUserId(userA);
        stubLeadRules(rule);
        Lead lead = lead(Lead.Source.WEBSITE, "Analytical Engines");
        when(leadRepository.findActiveByIdAndOrganizationId(leadId, organizationId)).thenReturn(Optional.of(lead));
        when(userRepository.findIdsByOrganizationIdAndTeamId(organizationId, teamId)).thenReturn(List.of(userA, userB, userC));

        listener.onRecordCreated(new CrmAuditEvents.RecordCreated(actorUserId, organizationId, "Lead", leadId));

        assertThat(lead.getOwnerId()).isEqualTo(userB);
        assertThat(rule.getLastAssignedUserId()).isEqualTo(userB);
    }

    @Test
    void onRecordCreated_teamAssignmentCursorAtLastMember_wrapsAroundToFirst() {
        UUID teamId = UUID.randomUUID();
        TerritoryRule rule = leadRule(TerritoryRule.MatchField.SOURCE, TerritoryRule.MatchOperator.EQUALS, "WEBSITE", null, teamId);
        rule.setLastAssignedUserId(userC);
        stubLeadRules(rule);
        Lead lead = lead(Lead.Source.WEBSITE, "Analytical Engines");
        when(leadRepository.findActiveByIdAndOrganizationId(leadId, organizationId)).thenReturn(Optional.of(lead));
        when(userRepository.findIdsByOrganizationIdAndTeamId(organizationId, teamId)).thenReturn(List.of(userA, userB, userC));

        listener.onRecordCreated(new CrmAuditEvents.RecordCreated(actorUserId, organizationId, "Lead", leadId));

        assertThat(lead.getOwnerId()).isEqualTo(userA);
    }

    @Test
    void onRecordCreated_teamAssignmentCursorNoLongerAMember_restartsFromFirst() {
        UUID teamId = UUID.randomUUID();
        TerritoryRule rule = leadRule(TerritoryRule.MatchField.SOURCE, TerritoryRule.MatchOperator.EQUALS, "WEBSITE", null, teamId);
        rule.setLastAssignedUserId(UUID.randomUUID()); // left the team, or the rule's target team just changed
        stubLeadRules(rule);
        Lead lead = lead(Lead.Source.WEBSITE, "Analytical Engines");
        when(leadRepository.findActiveByIdAndOrganizationId(leadId, organizationId)).thenReturn(Optional.of(lead));
        when(userRepository.findIdsByOrganizationIdAndTeamId(organizationId, teamId)).thenReturn(List.of(userA, userB));

        listener.onRecordCreated(new CrmAuditEvents.RecordCreated(actorUserId, organizationId, "Lead", leadId));

        assertThat(lead.getOwnerId()).isEqualTo(userA);
    }

    @Test
    void onRecordCreated_teamHasNoMembers_ruleIsSkippedAsIfItHadNotMatched() {
        UUID teamId = UUID.randomUUID();
        TerritoryRule rule = leadRule(TerritoryRule.MatchField.SOURCE, TerritoryRule.MatchOperator.EQUALS, "WEBSITE", null, teamId);
        stubLeadRules(rule);
        Lead lead = lead(Lead.Source.WEBSITE, "Analytical Engines");
        UUID originalOwner = lead.getOwnerId();
        when(leadRepository.findActiveByIdAndOrganizationId(leadId, organizationId)).thenReturn(Optional.of(lead));
        when(userRepository.findIdsByOrganizationIdAndTeamId(organizationId, teamId)).thenReturn(List.of());

        listener.onRecordCreated(new CrmAuditEvents.RecordCreated(actorUserId, organizationId, "Lead", leadId));

        assertThat(lead.getOwnerId()).isEqualTo(originalOwner);
        verify(leadRepository, never()).save(any());
        assertThat(rule.getMatchCount()).isZero();
    }

    @Test
    void onRecordCreated_matchValueCaseAndWhitespaceInsensitive() {
        TerritoryRule rule = leadRule(TerritoryRule.MatchField.COMPANY_NAME, TerritoryRule.MatchOperator.CONTAINS, "ENGINES", userA, null);
        stubLeadRules(rule);
        Lead lead = lead(Lead.Source.OTHER, "analytical engines inc");
        when(leadRepository.findActiveByIdAndOrganizationId(leadId, organizationId)).thenReturn(Optional.of(lead));

        listener.onRecordCreated(new CrmAuditEvents.RecordCreated(actorUserId, organizationId, "Lead", leadId));

        assertThat(lead.getOwnerId()).isEqualTo(userA);
    }

    @Test
    void onRecordCreated_publishedEventCarriesTheNewOwnerAndOriginalActor() {
        TerritoryRule rule = leadRule(TerritoryRule.MatchField.SOURCE, TerritoryRule.MatchOperator.EQUALS, "WEBSITE", userA, null);
        stubLeadRules(rule);
        Lead lead = lead(Lead.Source.WEBSITE, "Analytical Engines");
        when(leadRepository.findActiveByIdAndOrganizationId(leadId, organizationId)).thenReturn(Optional.of(lead));

        listener.onRecordCreated(new CrmAuditEvents.RecordCreated(actorUserId, organizationId, "Lead", leadId));

        ArgumentCaptor<CrmAuditEvents.RecordAssigned> captor = ArgumentCaptor.forClass(CrmAuditEvents.RecordAssigned.class);
        verify(events).publishEvent(captor.capture());
        CrmAuditEvents.RecordAssigned published = captor.getValue();
        assertThat(published.actorUserId()).isEqualTo(actorUserId);
        assertThat(published.resourceType()).isEqualTo("Lead");
        assertThat(published.resourceId()).isEqualTo(leadId);
        assertThat(published.newOwnerId()).isEqualTo(userA);
    }

    private void stubLeadRules(TerritoryRule... rules) {
        when(territoryRuleRepository.findByOrganizationIdAndTargetResourceAndActiveTrueOrderByPriorityAsc(
                        organizationId, TerritoryRule.TargetResource.LEAD))
                .thenReturn(List.of(rules));
    }

    private TerritoryRule leadRule(
            TerritoryRule.MatchField field, TerritoryRule.MatchOperator operator, String value, UUID assignToUserId, UUID assignToTeamId) {
        TerritoryRule rule = new TerritoryRule(organizationId, "Rule", TerritoryRule.TargetResource.LEAD, field, operator, value);
        rule.setId(UUID.randomUUID());
        rule.setAssignToUserId(assignToUserId);
        rule.setAssignToTeamId(assignToTeamId);
        return rule;
    }

    private Lead lead(Lead.Source source, String companyName) {
        Lead lead = new Lead(organizationId, "Ada", "Lovelace", UUID.randomUUID());
        lead.setId(leadId);
        lead.setSource(source);
        lead.setCompanyName(companyName);
        return lead;
    }
}
