package com.aitrainercrm.platform.territory.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.lead.entity.Lead;
import com.aitrainercrm.platform.organization.repository.TeamRepository;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.territory.dto.CreateTerritoryRuleRequest;
import com.aitrainercrm.platform.territory.dto.UpdateTerritoryRuleRequest;
import com.aitrainercrm.platform.territory.entity.TerritoryRule;
import com.aitrainercrm.platform.territory.repository.TerritoryRuleRepository;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD for {@link TerritoryRule} - entirely gated by {@code TERRITORY_RULE:*:ORGANIZATION}, same
 * "no ScopeAuthorizationService call" shape {@code SlaPolicyService}/{@code CustomFieldService}
 * use. The actual routing logic (matching a rule's criterion against a real Lead/Account and
 * reassigning it) lives in {@code TerritoryAssignmentListener}, not here - this class only ever
 * validates and persists rule definitions.
 */
@Service
@RequiredArgsConstructor
public class TerritoryRuleService {

    private static final Set<TerritoryRule.MatchField> LEAD_FIELDS =
            Set.of(TerritoryRule.MatchField.SOURCE, TerritoryRule.MatchField.COMPANY_NAME);
    private static final Set<TerritoryRule.MatchField> ACCOUNT_FIELDS = Set.of(
            TerritoryRule.MatchField.INDUSTRY, TerritoryRule.MatchField.BILLING_COUNTRY, TerritoryRule.MatchField.BILLING_STATE);

    private final TerritoryRuleRepository territoryRuleRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<TerritoryRule> list(UserPrincipal principal, Pageable pageable) {
        return territoryRuleRepository.findByOrganizationIdOrderByTargetResourceAscPriorityAsc(principal.getOrganizationId(), pageable);
    }

    @Transactional(readOnly = true)
    public TerritoryRule get(UserPrincipal principal, UUID ruleId) {
        return findOrThrow(principal.getOrganizationId(), ruleId);
    }

    @Transactional
    public TerritoryRule create(UserPrincipal principal, CreateTerritoryRuleRequest request) {
        UUID organizationId = principal.getOrganizationId();
        assertFieldValidForResource(request.targetResource(), request.matchField());
        assertMatchValueValid(request.matchField(), request.matchValue());
        assertExactlyOneAssignmentTarget(organizationId, request.assignToUserId(), request.assignToTeamId());

        TerritoryRule rule = new TerritoryRule(
                organizationId, request.name(), request.targetResource(), request.matchField(), request.matchOperator(), request.matchValue());
        rule.setPriority(request.priority());
        rule.setAssignToUserId(request.assignToUserId());
        rule.setAssignToTeamId(request.assignToTeamId());
        territoryRuleRepository.save(rule);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), organizationId, "TerritoryRule", rule.getId()));
        return rule;
    }

    /** targetResource isn't editable - see UpdateTerritoryRuleRequest's javadoc. Everything else (including which field/operator/value it matches on) can change freely. */
    @Transactional
    public TerritoryRule update(UserPrincipal principal, UUID ruleId, UpdateTerritoryRuleRequest request) {
        UUID organizationId = principal.getOrganizationId();
        TerritoryRule rule = findOrThrow(organizationId, ruleId);
        assertFieldValidForResource(rule.getTargetResource(), request.matchField());
        assertMatchValueValid(request.matchField(), request.matchValue());
        assertExactlyOneAssignmentTarget(organizationId, request.assignToUserId(), request.assignToTeamId());

        rule.setName(request.name());
        rule.setMatchField(request.matchField());
        rule.setMatchOperator(request.matchOperator());
        rule.setMatchValue(request.matchValue());
        rule.setPriority(request.priority());
        // Switching assignment target (user <-> team, or to a different team) resets the
        // round-robin cursor - the old cursor was a position in a rotation that may no longer
        // exist.
        if (!Objects.equals(rule.getAssignToTeamId(), request.assignToTeamId())) {
            rule.setLastAssignedUserId(null);
        }
        rule.setAssignToUserId(request.assignToUserId());
        rule.setAssignToTeamId(request.assignToTeamId());
        rule.setActive(request.active());
        territoryRuleRepository.save(rule);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), organizationId, "TerritoryRule", rule.getId()));
        return rule;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID ruleId) {
        TerritoryRule rule = findOrThrow(principal.getOrganizationId(), ruleId);
        territoryRuleRepository.delete(rule);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "TerritoryRule", ruleId));
    }

    private void assertFieldValidForResource(TerritoryRule.TargetResource resource, TerritoryRule.MatchField field) {
        Set<TerritoryRule.MatchField> valid = resource == TerritoryRule.TargetResource.LEAD ? LEAD_FIELDS : ACCOUNT_FIELDS;
        if (!valid.contains(field)) {
            throw new BusinessException(
                    "TERRITORY_RULE_INVALID_FIELD",
                    "%s is not a valid match field for %s rules".formatted(field, resource),
                    HttpStatus.BAD_REQUEST);
        }
    }

    /** SOURCE is the one matchField backed by a real enum (Lead.Source) rather than free text - validated here so a rule with a typo'd source value silently never matches anything, instead of failing loudly at creation time. */
    private void assertMatchValueValid(TerritoryRule.MatchField field, String matchValue) {
        if (field != TerritoryRule.MatchField.SOURCE) return;
        try {
            Lead.Source.valueOf(matchValue.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    "TERRITORY_RULE_INVALID_MATCH_VALUE",
                    "'%s' isn't a valid lead source".formatted(matchValue),
                    HttpStatus.BAD_REQUEST);
        }
    }

    private void assertExactlyOneAssignmentTarget(UUID organizationId, UUID assignToUserId, UUID assignToTeamId) {
        boolean hasUser = assignToUserId != null;
        boolean hasTeam = assignToTeamId != null;
        if (hasUser == hasTeam) {
            throw new BusinessException(
                    "TERRITORY_RULE_INVALID_ASSIGNMENT",
                    "Exactly one of assignToUserId or assignToTeamId must be set",
                    HttpStatus.BAD_REQUEST);
        }
        if (hasUser) {
            boolean exists = userRepository.findActiveById(assignToUserId).map(u -> organizationId.equals(u.getOrganizationId())).orElse(false);
            if (!exists) {
                throw new ResourceNotFoundException("User", assignToUserId);
            }
        } else {
            if (teamRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(assignToTeamId, organizationId).isEmpty()) {
                throw new ResourceNotFoundException("Team", assignToTeamId);
            }
        }
    }

    private TerritoryRule findOrThrow(UUID organizationId, UUID ruleId) {
        return territoryRuleRepository.findByIdAndOrganizationId(ruleId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("TerritoryRule", ruleId));
    }
}
