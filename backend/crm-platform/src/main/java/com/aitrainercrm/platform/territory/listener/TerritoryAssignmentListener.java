package com.aitrainercrm.platform.territory.listener;

import com.aitrainercrm.platform.account.entity.Account;
import com.aitrainercrm.platform.account.repository.AccountRepository;
import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.lead.entity.Lead;
import com.aitrainercrm.platform.lead.repository.LeadRepository;
import com.aitrainercrm.platform.territory.entity.TerritoryRule;
import com.aitrainercrm.platform.territory.repository.TerritoryRuleRepository;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Auto-assigns a newly created Lead or Account to an owner, per {@link TerritoryRule}. The fourth
 * independent {@code @EventListener} on the {@link CrmAuditEvents} bus, alongside {@code
 * WebhookDispatchListener}, {@code AuditEventListener}, and {@code WorkflowEngineListener} -
 * LeadService/AccountService have no idea this listener exists.
 *
 * <p>Unlike every other listener on this bus, this is the one module this session that
 * deliberately writes back to another module's core {@code ownerId} column instead of staying
 * purely additive (contrast with {@code SlaEvaluationService}, which reads {@code Ticket} fields
 * but never writes to {@code tickets}). That's safe here specifically because this only ever
 * fires once, on {@code RecordCreated}, before any human has had a chance to touch the record -
 * there's no risk of silently reassigning a lead or account someone is already mid-conversation
 * with. That's also why there's no {@code onRecordUpdated} handler: re-running territory matching
 * against edits to an already-owned record is a different (and much riskier) feature this module
 * doesn't attempt.
 *
 * <p><b>Round-robin algorithm</b> (only used when a rule's {@code assignToTeamId} is set): fetch
 * the team's current member ids via {@link UserRepository#findIdsByOrganizationIdAndTeamId},
 * sort them deterministically by {@link UUID#compareTo}, and advance one position past {@link
 * TerritoryRule#getLastAssignedUserId()} in that sorted list, wrapping around at the end. If the
 * cursor is null or no longer a member of the team (they left, or the rule's target team just
 * changed), the rotation restarts from the first member. A team with zero members is treated as
 * "rule doesn't currently resolve to anyone" and is skipped entirely, as if it hadn't matched.
 */
@Component
@RequiredArgsConstructor
public class TerritoryAssignmentListener {

    private final TerritoryRuleRepository territoryRuleRepository;
    private final LeadRepository leadRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;

    @Async
    @EventListener
    @Transactional
    public void onRecordCreated(CrmAuditEvents.RecordCreated event) {
        TerritoryRule.TargetResource resource = parseResource(event.resourceType());
        if (resource == null) return;

        List<TerritoryRule> rules = territoryRuleRepository
                .findByOrganizationIdAndTargetResourceAndActiveTrueOrderByPriorityAsc(event.organizationId(), resource);
        if (rules.isEmpty()) return;

        if (resource == TerritoryRule.TargetResource.LEAD) {
            assignLead(event, rules);
        } else {
            assignAccount(event, rules);
        }
    }

    private void assignLead(CrmAuditEvents.RecordCreated event, List<TerritoryRule> rules) {
        Lead lead = leadRepository.findActiveByIdAndOrganizationId(event.resourceId(), event.organizationId()).orElse(null);
        if (lead == null) return;

        for (TerritoryRule rule : rules) {
            String fieldValue = switch (rule.getMatchField()) {
                case SOURCE -> lead.getSource() == null ? null : lead.getSource().name();
                case COMPANY_NAME -> lead.getCompanyName();
                default -> null;
            };
            if (!matches(rule, fieldValue)) continue;

            UUID assignee = resolveAssignee(rule);
            if (assignee == null) continue;

            lead.setOwnerId(assignee);
            leadRepository.save(lead);
            recordMatchAndPublish(event, rule, assignee);
            return;
        }
    }

    private void assignAccount(CrmAuditEvents.RecordCreated event, List<TerritoryRule> rules) {
        Account account = accountRepository.findActiveByIdAndOrganizationId(event.resourceId(), event.organizationId()).orElse(null);
        if (account == null) return;

        for (TerritoryRule rule : rules) {
            String fieldValue = switch (rule.getMatchField()) {
                case INDUSTRY -> account.getIndustry();
                case BILLING_COUNTRY -> account.getBillingCountry();
                case BILLING_STATE -> account.getBillingState();
                default -> null;
            };
            if (!matches(rule, fieldValue)) continue;

            UUID assignee = resolveAssignee(rule);
            if (assignee == null) continue;

            account.setOwnerId(assignee);
            accountRepository.save(account);
            recordMatchAndPublish(event, rule, assignee);
            return;
        }
    }

    private boolean matches(TerritoryRule rule, String fieldValue) {
        if (fieldValue == null || fieldValue.isBlank()) return false;
        String actual = fieldValue.toLowerCase(Locale.ROOT);
        String expected = rule.getMatchValue().toLowerCase(Locale.ROOT);
        return rule.getMatchOperator() == TerritoryRule.MatchOperator.EQUALS
                ? actual.equals(expected)
                : actual.contains(expected);
    }

    private UUID resolveAssignee(TerritoryRule rule) {
        if (rule.getAssignToUserId() != null) {
            return rule.getAssignToUserId();
        }
        List<UUID> members = userRepository.findIdsByOrganizationIdAndTeamId(rule.getOrganizationId(), rule.getAssignToTeamId());
        if (members.isEmpty()) return null;

        members.sort(Comparator.naturalOrder());
        UUID cursor = rule.getLastAssignedUserId();
        if (cursor == null) return members.get(0);

        int cursorIndex = members.indexOf(cursor);
        if (cursorIndex < 0) return members.get(0);
        return members.get((cursorIndex + 1) % members.size());
    }

    private void recordMatchAndPublish(CrmAuditEvents.RecordCreated event, TerritoryRule rule, UUID assignee) {
        rule.recordMatch(assignee);
        territoryRuleRepository.save(rule);
        events.publishEvent(new CrmAuditEvents.RecordAssigned(
                event.actorUserId(), event.organizationId(), event.resourceType(), event.resourceId(), assignee));
    }

    private TerritoryRule.TargetResource parseResource(String resourceType) {
        return switch (resourceType) {
            case "Lead" -> TerritoryRule.TargetResource.LEAD;
            case "Account" -> TerritoryRule.TargetResource.ACCOUNT;
            default -> null;
        };
    }
}
