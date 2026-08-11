package com.aitrainercrm.platform.invoice.dto;

import com.aitrainercrm.platform.invoice.entity.InvoiceLineItem;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;

@Builder
public record InvoiceLineItemDto(
        UUID id, UUID productId, String description, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {

    public static InvoiceLineItemDto from(InvoiceLineItem lineItem) {
        return InvoiceLineItemDto.builder()
                .id(lineItem.getId())
                .productId(lineItem.getProductId())
                .description(lineItem.getDescription())
                .quantity(lineItem.getQuantity())
                .unitPrice(lineItem.getUnitPrice())
                .lineTotal(lineItem.getLineTotal())
                .build();
    }
}
