package com.aitrainercrm.platform.quote.dto;

import com.aitrainercrm.platform.quote.entity.Quote;
import com.aitrainercrm.platform.quote.entity.QuoteLineItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
public record QuoteDto(
        UUID id,
        UUID opportunityId,
        String name,
        Quote.Status status,
        String currency,
        LocalDate validUntil,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        UUID ownerId,
        Instant createdAt,
        Instant updatedAt,
        List<QuoteLineItemDto> lineItems) {

    /** Header-only shape, for list endpoints where fetching every quote's line items would be wasteful. */
    public static QuoteDto from(Quote quote) {
        return from(quote, List.of());
    }

    public static QuoteDto from(Quote quote, List<QuoteLineItem> lineItems) {
        return QuoteDto.builder()
                .id(quote.getId())
                .opportunityId(quote.getOpportunityId())
                .name(quote.getName())
                .status(quote.getStatus())
                .currency(quote.getCurrency())
                .validUntil(quote.getValidUntil())
                .subtotal(quote.getSubtotal())
                .discountAmount(quote.getDiscountAmount())
                .taxAmount(quote.getTaxAmount())
                .totalAmount(quote.getTotalAmount())
                .ownerId(quote.getOwnerId())
                .createdAt(quote.getCreatedAt())
                .updatedAt(quote.getUpdatedAt())
                .lineItems(lineItems.stream().map(QuoteLineItemDto::from).toList())
                .build();
    }
}
