package com.aitrainercrm.platform.quote.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One priced line on a {@link Quote}. {@link #quoteId} is a real foreign key
 * with {@code on delete cascade} (see V5's migration) - unlike Activity's
 * polymorphic related-to reference, a line item only ever belongs to one
 * quote, so there's nothing to work around here. {@link #productId} is
 * optional: a line item can be a one-off/custom charge with no catalog
 * product behind it, in which case {@link #description}/{@link #unitPrice}
 * are simply whatever the caller typed rather than copied from a Product.
 */
@Entity
@Table(name = "quote_line_items")
@Getter
@Setter
@NoArgsConstructor
public class QuoteLineItem extends BaseEntity {

    @Column(name = "quote_id", nullable = false)
    private UUID quoteId;

    @Column(name = "product_id")
    private UUID productId;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false)
    private int quantity = 1;

    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineTotal = BigDecimal.ZERO;

    public QuoteLineItem(UUID quoteId, String description, int quantity, BigDecimal unitPrice) {
        this.quoteId = quoteId;
        this.description = description;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        recomputeLineTotal();
    }

    public void recomputeLineTotal() {
        this.lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
