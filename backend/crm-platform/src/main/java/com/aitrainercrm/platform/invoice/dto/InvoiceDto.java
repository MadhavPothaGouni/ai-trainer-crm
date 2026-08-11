package com.aitrainercrm.platform.invoice.dto;

import com.aitrainercrm.platform.invoice.entity.Invoice;
import com.aitrainercrm.platform.invoice.entity.InvoiceLineItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
public record InvoiceDto(
        UUID id,
        UUID orderId,
        String invoiceNumber,
        Invoice.Status status,
        String currency,
        LocalDate issueDate,
        LocalDate dueDate,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        BigDecimal amountPaid,
        BigDecimal balanceDue,
        Instant createdAt,
        Instant updatedAt,
        List<InvoiceLineItemDto> lineItems) {

    /** Header-only shape, for list endpoints where fetching every invoice's line items would be wasteful. */
    public static InvoiceDto from(Invoice invoice) {
        return from(invoice, List.of());
    }

    public static InvoiceDto from(Invoice invoice, List<InvoiceLineItem> lineItems) {
        return InvoiceDto.builder()
                .id(invoice.getId())
                .orderId(invoice.getOrderId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .status(invoice.getStatus())
                .currency(invoice.getCurrency())
                .issueDate(invoice.getIssueDate())
                .dueDate(invoice.getDueDate())
                .subtotal(invoice.getSubtotal())
                .discountAmount(invoice.getDiscountAmount())
                .taxAmount(invoice.getTaxAmount())
                .totalAmount(invoice.getTotalAmount())
                .amountPaid(invoice.getAmountPaid())
                .balanceDue(invoice.balanceDue())
                .createdAt(invoice.getCreatedAt())
                .updatedAt(invoice.getUpdatedAt())
                .lineItems(lineItems.stream().map(InvoiceLineItemDto::from).toList())
                .build();
    }
}
