package com.aitrainercrm.platform.leadscoring.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single scoring criterion: whenever a Lead matches {@link #matchField}/{@link
 * #matchOperator}/{@link #matchValue}, it contributes {@link #points} (positive or negative) to
 * that Lead's {@code Lead#score}. See V24's migration comment for the two ways this deliberately
 * differs from {@code TerritoryRule}, the closest precedent: every ACTIVE matching rule
 * contributes (there is no "first match wins" and therefore no priority column), and {@code
 * LeadScoringEngine} recomputes on both create AND update, not just create.
 *
 * <p>Unlike {@code TerritoryRule.MatchField}, {@link MatchField} has no second target resource to
 * share the table with, so there's no field/resource pairing for {@code LeadScoringRuleService} to
 * validate - every field here is a Lead field.
 */
@Entity
@Table(name = "lead_scoring_rules")
@Getter
@Setter
@NoArgsConstructor
public class LeadScoringRule extends BaseEntity {

    public enum MatchField {
        SOURCE, COMPANY_NAME, TITLE, EMAIL_DOMAIN
    }

    public enum MatchOperator {
        EQUALS, CONTAINS
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_field", nullable = false, length = 30)
    private MatchField matchField;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_operator", nullable = false, length = 20)
    private MatchOperator matchOperator;

    @Column(name = "match_value", nullable = false, length = 200)
    private String matchValue;

    /** Can be negative - a rule can penalize a Lead's score just as easily as it can boost it (e.g. "source = COLD_CALL: -10"). */
    @Column(nullable = false)
    private int points;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "match_count", nullable = false)
    private int matchCount = 0;

    @Column(name = "last_matched_at")
    private Instant lastMatchedAt;

    public LeadScoringRule(
            UUID organizationId, String name, MatchField matchField, MatchOperator matchOperator, String matchValue, int points) {
        this.organizationId = organizationId;
        this.name = name;
        this.matchField = matchField;
        this.matchOperator = matchOperator;
        this.matchValue = matchValue;
        this.points = points;
    }

    /** Called by LeadScoringEngine every time this rule's criterion matches a Lead being (re)scored, regardless of whether the Lead's total score actually changes as a result. */
    public void recordMatch() {
        this.matchCount++;
        this.lastMatchedAt = Instant.now();
    }
}
