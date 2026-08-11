package com.aitrainercrm.platform.order.dto;

import com.aitrainercrm.platform.order.entity.Order;
import com.aitrainercrm.platform.order.entity.OrderLineItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
public record OrderDto(
        UUID id,
        UUID quoteId,
        String orderNumber,
        Order.Status status,
        String currency,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        Instant createdAt,
        Instant updatedAt,
        List<OrderLineItemDto> lineItems) {

    /** Header-only shape, for list endpoints where fetching every order's line items would be wasteful. */
    public static OrderDto from(Order order) {
        return from(order, List.of());
    }

    public static OrderDto from(Order order, List<OrderLineItem> lineItems) {
        return OrderDto.builder()
                .id(order.getId())
                .quoteId(order.getQuoteId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus())
                .currency(order.getCurrency())
                .subtotal(order.getSubtotal())
                .discountAmount(order.getDiscountAmount())
                .taxAmount(order.getTaxAmount())
                .totalAmount(order.getTotalAmount())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .lineItems(lineItems.stream().map(OrderLineItemDto::from).toList())
                .build();
    }
}
