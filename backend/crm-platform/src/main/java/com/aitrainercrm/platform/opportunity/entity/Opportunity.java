package com.aitrainercrm.platform.opportunity.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A sales pipeline item tied to an {@link com.aitrainercrm.platform.account.entity.Account} -
 * this platform's term for what's commonly called a "deal" (matches
 * {@link com.aitrainercrm.platform.role.entity.Permission.Resource#OPPORTUNITY},
 * already seeded in V2's permission catalog).
 */
@Entity
@Table(name = "opportunities")
@Getter
@Setter
@NoArgsConstructor
public class Opportunity extends BaseEntity {

    public enum Stage {
        PROSPECTING, QUALIFICATION, PROPOSAL, NEGOTIATION, CLOSED_WON, CLOSED_LOST;

        public boolean isClosed() {
            return this == CLOSED_WON || this == CLOSED_LOST;
        }
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    /** Unlike Contact#accountId, this is required - an opportunity is always pipeline for a specific company. */
    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "primary_contact_id")
    private UUID primaryContactId;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Stage stage = Stage.PROSPECTING;

    @Column(precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(length = 3)
    private String currency;

    @Column(name = "expected_close_date")
    private LocalDate expectedCloseDate;

    /** Set automatically the moment stage transitions into CLOSED_WON/CLOSED_LOST - see OpportunityService#updateStage. */
    @Column(name = "actual_close_date")
    private LocalDate actualCloseDate;

    @Column(length = 2000)
    private String description;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Opportunity(UUID organizationId, UUID accountId, String name, UUID ownerId) {
        this.organizationId = organizationId;
        this.accountId = accountId;
        this.name = name;
        this.ownerId = ownerId;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
