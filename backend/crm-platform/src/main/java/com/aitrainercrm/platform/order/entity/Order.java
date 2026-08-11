package com.aitrainercrm.platform.order.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A confirmed sale, optionally converted from a {@link com.aitrainercrm.platform.quote.entity.Quote}
 * (see {@link #quoteId}) via {@code OrderService#createFromQuote}, or created
 * directly. Like {@link com.aitrainercrm.platform.product.entity.Product},
 * there's no {@code ownerId} - ORDER is seeded in V2 at TEAM/DEPARTMENT/
 * ORGANIZATION scope only (no OWN), so {@code OrderService} does no
 * per-record ScopeAuthorizationService check; the controller's
 * {@code @PreAuthorize} is the whole authorization story.
 *
 * <p>{@link #status} moves DRAFT -&gt; CONFIRMED -&gt; FULFILLED, with CANCELLED
 * reachable from DRAFT or CONFIRMED. DRAFT -&gt; CONFIRMED is deliberately not
 * just another {@code UPDATE} - it's gated on the separate {@code
 * ORDER:APPROVE} permission the catalog already seeded for exactly this kind
 * of "sign off on it" action (see {@code OrderService#confirm}), while
 * CONFIRMED -&gt; FULFILLED and either -&gt; CANCELLED go through the ordinary
 * {@code ORDER:UPDATE}-gated {@code OrderService#updateStatus}.
 *
 * <p>{@link #subtotal}/{@link #totalAmount} are stamped, recomputed by
 * {@code OrderService} on every line item change - same reasoning as
 * {@code Quote}.
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order extends BaseEntity {

    public enum Status {
        DRAFT, CONFIRMED, FULFILLED, CANCELLED
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    /** Null when the order wasn't converted from a quote. */
    @Column(name = "quote_id")
    private UUID quoteId;

    @Column(name = "order_number", nullable = false, length = 50)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(length = 3)
    private String currency;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Order(UUID organizationId, String orderNumber) {
        this.organizationId = organizationId;
        this.orderNumber = orderNumber;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
