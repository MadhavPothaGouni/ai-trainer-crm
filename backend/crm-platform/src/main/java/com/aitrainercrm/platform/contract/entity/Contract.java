package com.aitrainercrm.platform.contract.entity;

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
 * The ongoing legal/subscription relationship with a customer, tracked after a deal closes - the
 * resource this whole module exists to fill in; see V35's migration comment for why Quote/Order/
 * Invoice each fall short of this and why this mirrors {@link com.aitrainercrm.platform.ticket.entity.Ticket}'s
 * owner-scoped shape rather than inventing a new one.
 */
@Entity
@Table(name = "contracts")
@Getter
@Setter
@NoArgsConstructor
public class Contract extends BaseEntity {

    public enum Status {
        DRAFT, ACTIVE, EXPIRED, TERMINATED, RENEWED
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    /** Nullable - a renewal contract has no opportunity behind it; a fresh contract usually does. See V35's migration comment. */
    @Column(name = "opportunity_id")
    private UUID opportunityId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "contract_number", nullable = false, length = 50)
    private String contractNumber;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "total_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalValue = BigDecimal.ZERO;

    @Column(name = "auto_renew", nullable = false)
    private boolean autoRenew;

    /** Only meaningful when {@code autoRenew} is true - not enforced at the database level, see V35's migration comment. */
    @Column(name = "renewal_term_months")
    private Integer renewalTermMonths;

    @Column(name = "signed_at")
    private Instant signedAt;

    @Column(length = 4000)
    private String terms;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Contract(UUID organizationId, UUID accountId, UUID ownerId, String contractNumber, String title, LocalDate startDate, LocalDate endDate) {
        this.organizationId = organizationId;
        this.accountId = accountId;
        this.ownerId = ownerId;
        this.contractNumber = contractNumber;
        this.title = title;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
