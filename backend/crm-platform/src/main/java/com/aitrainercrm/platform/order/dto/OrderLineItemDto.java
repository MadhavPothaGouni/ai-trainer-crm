package com.aitrainercrm.platform.order.dto;

import com.aitrainercrm.platform.order.entity.OrderLineItem;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;

@Builder
public record OrderLineItemDto(
        UUID id, UUID productId, String description, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {

    public static OrderLineItemDto from(OrderLineItem lineItem) {
        return OrderLineItemDto.builder()
                .id(lineItem.getId())
                .productId(lineItem.getProductId())
                .description(lineItem.getDescription())
                .quantity(lineItem.getQuantity())
                .unitPrice(lineItem.getUnitPrice())
                .lineTotal(lineItem.getLineTotal())
                .build();
    }
}
