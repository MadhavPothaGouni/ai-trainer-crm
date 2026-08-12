package com.aitrainercrm.platform.leadscoring.listener;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.lead.entity.Lead;
import com.aitrainercrm.platform.lead.repository.LeadRepository;
import com.aitrainercrm.platform.leadscoring.entity.LeadScoringRule;
import com.aitrainercrm.platform.leadscoring.repository.LeadScoringRuleRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recomputes {@code Lead#score} whenever a Lead is created or updated - the seventh {@code
 * @EventListener} on the {@link CrmAuditEvents} bus, alongside {@code WebhookDispatchListener},
 * {@code AuditEventListener}, {@code WorkflowEngineListener}, {@code TerritoryAssignmentListener},
 * and {@code DuplicateDetectionListener}.
 *
 * <p>Two deliberate differences from {@code TerritoryAssignmentListener}, the closest precedent -
 * see V24's migration comment for the full reasoning: every {@code ACTIVE} matching rule
 * contributes its points (there is no "first match wins," so rule order never matters), and this
 * listener reacts to {@code onRecordUpdated} as well as {@code onRecordCreated}, since a stale
 * score would actively mislead a prioritization feature the way a stale territory assignment or
 * duplicate flag wouldn't.
 *
 * <p>Like {@code DuplicateDetectionListener}, this is purely additive in the sense that it never
 * touches anything about the Lead except {@link Lead#getScore()} itself - unlike {@code
 * TerritoryAssignmentListener}, which reassigns {@code ownerId}, a field every other part of the
 * platform also reads and writes. Nothing else in this codebase writes {@code Lead#score} outside
 * this class.
 */
@Component
@RequiredArgsConstructor
public class LeadScoringEngine {

    private final LeadScoringRuleRepository leadScoringRuleRepository;
    private final LeadRepository leadRepository;

    @Async
    @EventListener
    @Transactional
    public void onRecordCreated(CrmAuditEvents.RecordCreated event) {
        if (!"Lead".equals(event.resourceType())) return;
        rescore(event.organizationId(), event.resourceId());
    }

    @Async
    @EventListener
    @Transactional
    public void onRecordUpdated(CrmAuditEvents.RecordUpdated event) {
        if (!"Lead".equals(event.resourceType())) return;
        rescore(event.organizationId(), event.resourceId());
    }

    private void rescore(UUID organizationId, UUID leadId) {
        Lead lead = leadRepository.findActiveByIdAndOrganizationId(leadId, organizationId).orElse(null);
        if (lead == null) return;

        List<LeadScoringRule> rules = leadScoringRuleRepository.findByOrganizationIdAndActiveTrue(organizationId);
        if (rules.isEmpty()) {
            if (lead.getScore() != 0) {
                lead.setScore(0);
                leadRepository.save(lead);
            }
            return;
        }

        int total = 0;
        for (LeadScoringRule rule : rules) {
            if (!matches(rule, fieldValue(lead, rule.getMatchField()))) continue;
            total += rule.getPoints();
            rule.recordMatch();
        }
        leadScoringRuleRepository.saveAll(rules);

        lead.setScore(total);
        leadRepository.save(lead);
    }

    private String fieldValue(Lead lead, LeadScoringRule.MatchField field) {
        return switch (field) {
            case SOURCE -> lead.getSource() == null ? null : lead.getSource().name();
            case COMPANY_NAME -> lead.getCompanyName();
            case TITLE -> lead.getTitle();
            case EMAIL_DOMAIN -> emailDomain(lead.getEmail());
        };
    }

    private String emailDomain(String email) {
        if (email == null) return null;
        int at = email.indexOf('@');
        return at < 0 || at == email.length() - 1 ? null : email.substring(at + 1);
    }

    private boolean matches(LeadScoringRule rule, String fieldValue) {
        if (fieldValue == null || fieldValue.isBlank()) return false;
        String actual = fieldValue.toLowerCase(Locale.ROOT);
        String expected = rule.getMatchValue().toLowerCase(Locale.ROOT);
        return rule.getMatchOperator() == LeadScoringRule.MatchOperator.EQUALS
                ? actual.equals(expected)
                : actual.contains(expected);
    }
}
