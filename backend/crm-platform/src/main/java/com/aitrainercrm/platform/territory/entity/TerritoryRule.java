package com.aitrainercrm.platform.territory.entity;

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
 * Auto-routes a newly created Lead or Account to an owner - see V21's migration comment for why
 * this is a genuinely different concept from {@code Workflow} (which never touches who owns a
 * record) and the one module this session where an {@code @EventListener}
 * ({@code TerritoryAssignmentListener}) deliberately writes back to another module's core
 * {@code ownerId} column rather than staying purely additive.
 *
 * <p>{@link #matchField}/{@link #matchOperator}/{@link #matchValue} model one criterion, not a
 * boolean expression tree - a deliberate scope limit, see the migration comment. Which {@link
 * MatchField} values are valid depends on {@link #targetResource}: {@code SOURCE}/{@code
 * COMPANY_NAME} only make sense for {@code LEAD}, {@code INDUSTRY}/{@code BILLING_COUNTRY}/
 * {@code BILLING_STATE} only for {@code ACCOUNT} - {@code TerritoryRuleService} validates the
 * pairing at write time.
 *
 * <p>Exactly one of {@link #assignToUserId}/{@link #assignToTeamId} is set. A team assignment
 * round-robins across whichever users currently have that {@code teamId}, with {@link
 * #lastAssignedUserId} as the rotation's cursor - see {@code TerritoryAssignmentListener}'s
 * javadoc for the exact algorithm.
 */
@Entity
@Table(name = "territory_rules")
@Getter
@Setter
@NoArgsConstructor
public class TerritoryRule extends BaseEntity {

    public enum TargetResource {
        LEAD, ACCOUNT
    }

    public enum MatchField {
        /** LEAD only. */
        SOURCE,
        /** LEAD only. */
        COMPANY_NAME,
        /** ACCOUNT only. */
        INDUSTRY,
        /** ACCOUNT only. */
        BILLING_COUNTRY,
        /** ACCOUNT only. */
        BILLING_STATE
    }

    public enum MatchOperator {
        EQUALS, CONTAINS
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_resource", nullable = false, length = 20)
    private TargetResource targetResource;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_field", nullable = false, length = 30)
    private MatchField matchField;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_operator", nullable = false, length = 20)
    private MatchOperator matchOperator;

    @Column(name = "match_value", nullable = false, length = 200)
    private String matchValue;

    /** Ascending - lower runs first. The first ACTIVE rule (for this org+targetResource) whose criterion matches wins; later rules never even get evaluated. */
    @Column(nullable = false)
    private int priority = 100;

    @Column(name = "assign_to_user_id")
    private UUID assignToUserId;

    @Column(name = "assign_to_team_id")
    private UUID assignToTeamId;

    /** Round-robin cursor - only meaningful when assignToTeamId is set. Null means "no team assignment has happened yet," in which case the rotation starts from the team's first member in id order. */
    @Column(name = "last_assigned_user_id")
    private UUID lastAssignedUserId;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "match_count", nullable = false)
    private int matchCount = 0;

    @Column(name = "last_matched_at")
    private Instant lastMatchedAt;

    public TerritoryRule(
            UUID organizationId, String name, TargetResource targetResource, MatchField matchField, MatchOperator matchOperator, String matchValue) {
        this.organizationId = organizationId;
        this.name = name;
        this.targetResource = targetResource;
        this.matchField = matchField;
        this.matchOperator = matchOperator;
        this.matchValue = matchValue;
    }

    /** Called by TerritoryAssignmentListener every time this rule's criterion matches and it wins (even if the record ends up assigned to the same owner it already had). */
    public void recordMatch(UUID assignedUserId) {
        this.matchCount++;
        this.lastMatchedAt = Instant.now();
        if (assignToTeamId != null) {
            this.lastAssignedUserId = assignedUserId;
        }
    }
}
