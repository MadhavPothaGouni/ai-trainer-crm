package com.aitrainercrm.platform.territory.repository;

import com.aitrainercrm.platform.territory.entity.TerritoryRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TerritoryRuleRepository extends JpaRepository<TerritoryRule, UUID> {

    Optional<TerritoryRule> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Page<TerritoryRule> findByOrganizationIdOrderByTargetResourceAscPriorityAsc(UUID organizationId, Pageable pageable);

    /** The exact lookup TerritoryAssignmentListener runs on every new Lead/Account - active rules for this org+resource, cheapest (lowest priority number) first. First match wins; see idx_territory_rules_lookup (V21). */
    List<TerritoryRule> findByOrganizationIdAndTargetResourceAndActiveTrueOrderByPriorityAsc(UUID organizationId, TerritoryRule.TargetResource targetResource);
}
