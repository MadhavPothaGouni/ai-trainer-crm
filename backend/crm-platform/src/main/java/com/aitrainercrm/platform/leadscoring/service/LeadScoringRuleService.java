package com.aitrainercrm.platform.leadscoring.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.lead.entity.Lead;
import com.aitrainercrm.platform.leadscoring.dto.CreateLeadScoringRuleRequest;
import com.aitrainercrm.platform.leadscoring.dto.UpdateLeadScoringRuleRequest;
import com.aitrainercrm.platform.leadscoring.entity.LeadScoringRule;
import com.aitrainercrm.platform.leadscoring.repository.LeadScoringRuleRepository;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD for {@link LeadScoringRule} - entirely gated by {@code LEAD_SCORING_RULE:*:ORGANIZATION},
 * the same "no ScopeAuthorizationService call" third-kind shape {@code TerritoryRuleService}/
 * {@code SlaPolicyService} use. The actual scoring (matching a rule's criterion against a real
 * Lead and summing points onto it) lives in {@code LeadScoringEngine}, not here - this class only
 * ever validates and persists rule definitions.
 */
@Service
@RequiredArgsConstructor
public class LeadScoringRuleService {

    private final LeadScoringRuleRepository leadScoringRuleRepository;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<LeadScoringRule> list(UserPrincipal principal, Pageable pageable) {
        return leadScoringRuleRepository.findByOrganizationIdOrderByNameAsc(principal.getOrganizationId(), pageable);
    }

    @Transactional(readOnly = true)
    public LeadScoringRule get(UserPrincipal principal, UUID ruleId) {
        return findOrThrow(principal.getOrganizationId(), ruleId);
    }

    @Transactional
    public LeadScoringRule create(UserPrincipal principal, CreateLeadScoringRuleRequest request) {
        UUID organizationId = principal.getOrganizationId();
        assertMatchValueValid(request.matchField(), request.matchValue());

        LeadScoringRule rule = new LeadScoringRule(
                organizationId, request.name(), request.matchField(), request.matchOperator(), request.matchValue(), request.points());
        leadScoringRuleRepository.save(rule);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), organizationId, "LeadScoringRule", rule.getId()));
        return rule;
    }

    @Transactional
    public LeadScoringRule update(UserPrincipal principal, UUID ruleId, UpdateLeadScoringRuleRequest request) {
        UUID organizationId = principal.getOrganizationId();
        LeadScoringRule rule = findOrThrow(organizationId, ruleId);
        assertMatchValueValid(request.matchField(), request.matchValue());

        rule.setName(request.name());
        rule.setMatchField(request.matchField());
        rule.setMatchOperator(request.matchOperator());
        rule.setMatchValue(request.matchValue());
        rule.setPoints(request.points());
        rule.setActive(request.active());
        leadScoringRuleRepository.save(rule);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), organizationId, "LeadScoringRule", rule.getId()));
        return rule;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID ruleId) {
        LeadScoringRule rule = findOrThrow(principal.getOrganizationId(), ruleId);
        leadScoringRuleRepository.delete(rule);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "LeadScoringRule", ruleId));
    }

    /** SOURCE is the one matchField backed by a real enum (Lead.Source), same reasoning TerritoryRuleService#assertMatchValueValid documents - validated here so a typo'd source value doesn't just silently never match. */
    private void assertMatchValueValid(LeadScoringRule.MatchField field, String matchValue) {
        if (field != LeadScoringRule.MatchField.SOURCE) return;
        try {
            Lead.Source.valueOf(matchValue.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    "LEAD_SCORING_RULE_INVALID_MATCH_VALUE", "'%s' isn't a valid lead source".formatted(matchValue), HttpStatus.BAD_REQUEST);
        }
    }

    private LeadScoringRule findOrThrow(UUID organizationId, UUID ruleId) {
        return leadScoringRuleRepository.findByIdAndOrganizationId(ruleId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("LeadScoringRule", ruleId));
    }
}
