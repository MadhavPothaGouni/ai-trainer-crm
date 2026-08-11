package com.aitrainercrm.platform.invoice.entity;

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
 * One priced line on an {@link Invoice}. Same shape as
 * {@link com.aitrainercrm.platform.order.entity.OrderLineItem} - real FK
 * with {@code on delete cascade} (see V8's migration). {@code
 * InvoiceService#generateFromOrder} clones each of the source order's line
 * items into one of these, same verbatim-copy reasoning as
 * {@code OrderService#createFromQuote}. Only editable while the invoice is
 * still DRAFT - see {@code InvoiceService}'s line item methods.
 */
@Entity
@Table(name = "invoice_line_items")
@Getter
@Setter
@NoArgsConstructor
public class InvoiceLineItem extends BaseEntity {

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

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

    public InvoiceLineItem(UUID invoiceId, String description, int quantity, BigDecimal unitPrice) {
        this.invoiceId = invoiceId;
        this.description = description;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        recomputeLineTotal();
    }

    public void recomputeLineTotal() {
        this.lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
