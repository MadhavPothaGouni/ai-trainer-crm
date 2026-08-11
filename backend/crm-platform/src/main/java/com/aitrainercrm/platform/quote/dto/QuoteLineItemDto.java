package com.aitrainercrm.platform.quote.dto;

import com.aitrainercrm.platform.quote.entity.QuoteLineItem;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;

@Builder
public record QuoteLineItemDto(UUID id, UUID productId, String description, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {

    public static QuoteLineItemDto from(QuoteLineItem lineItem) {
        return QuoteLineItemDto.builder()
                .id(lineItem.getId())
                .productId(lineItem.getProductId())
                .description(lineItem.getDescription())
                .quantity(lineItem.getQuantity())
                .unitPrice(lineItem.getUnitPrice())
                .lineTotal(lineItem.getLineTotal())
                .build();
    }
}
