package com.aitrainercrm.platform.leadscoring.repository;

import com.aitrainercrm.platform.leadscoring.entity.LeadScoringRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadScoringRuleRepository extends JpaRepository<LeadScoringRule, UUID> {

    Optional<LeadScoringRule> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Page<LeadScoringRule> findByOrganizationIdOrderByNameAsc(UUID organizationId, Pageable pageable);

    /** The exact lookup LeadScoringEngine runs on every Lead create/update - every ACTIVE rule for this org, order doesn't matter since every match contributes (see idx_lead_scoring_rules_lookup, V24). */
    List<LeadScoringRule> findByOrganizationIdAndActiveTrue(UUID organizationId);
}
