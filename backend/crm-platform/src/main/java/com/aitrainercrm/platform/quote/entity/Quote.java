package com.aitrainercrm.platform.quote.entity;

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
 * A priced proposal tied to exactly one Opportunity - standard owner-scoped
 * CRM entity (QUOTE is in RoleService#isCoreCrmResource), same
 * ScopeAuthorizationService treatment as Account/Contact/Opportunity/Lead/
 * Activity. {@link #opportunityId} is intentionally never exposed for
 * update, unlike Contact.accountId/Opportunity.accountId - a quote moving to
 * a different deal after the fact doesn't make business sense the way
 * "oops, wrong account" does, so QuoteService#update simply never touches it.
 *
 * <p>{@link #subtotal}/{@link #totalAmount} are stamped columns, recomputed
 * by QuoteService any time a line item is added/changed/removed - see
 * QuoteService#recomputeTotals - rather than computed on every read, so a
 * plain {@code GET} doesn't need to join quote_line_items just to answer
 * "what's this quote worth."
 */
@Entity
@Table(name = "quotes")
@Getter
@Setter
@NoArgsConstructor
public class Quote extends BaseEntity {

    public enum Status {
        DRAFT, SENT, ACCEPTED, REJECTED
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "opportunity_id", nullable = false)
    private UUID opportunityId;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(length = 3)
    private String currency;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Quote(UUID organizationId, UUID opportunityId, String name, UUID ownerId) {
        this.organizationId = organizationId;
        this.opportunityId = opportunityId;
        this.name = name;
        this.ownerId = ownerId;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
